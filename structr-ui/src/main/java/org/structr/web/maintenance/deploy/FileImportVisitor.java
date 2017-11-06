/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.maintenance.deploy;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import static org.structr.core.graph.NodeInterface.name;
import org.structr.core.graph.Tx;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.web.common.FileHelper;
import org.structr.web.common.ImageHelper;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.AbstractMinifiedFile;
import org.structr.web.entity.File;
import org.structr.web.entity.Folder;
import org.structr.web.entity.Image;
import org.structr.web.entity.relation.MinificationSource;
import org.structr.web.entity.relation.Thumbnails;

/**
 *
 */
public class FileImportVisitor implements FileVisitor<Path> {

	private static final Logger logger      = LoggerFactory.getLogger(FileImportVisitor.class.getName());
	private Map<String, Object> config      = null;
	private SecurityContext securityContext = null;
	private Path basePath                   = null;
	private App app                         = null;
	private List<File> deferredFiles    = null;

	public FileImportVisitor(final Path basePath, final Map<String, Object> config) {

		this.securityContext = SecurityContext.getSuperUserInstance();
		this.securityContext.setDoTransactionNotifications(false);
		this.basePath        = basePath;
		this.config          = config;
		this.app             = StructrApp.getInstance(this.securityContext);
		this.deferredFiles   = new ArrayList<>();
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {

		if (attrs.isDirectory()) {

			createFolder(file);

		} else if (attrs.isRegularFile()) {

			final String fileName = file.getFileName().toString();

			createFile(file, fileName);
		}

		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {

		logger.warn("Exception while importing file {}: {}", new Object[] { file.toString(), exc.getMessage() });
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	public void handleDeferredFiles() {

		if (!this.deferredFiles.isEmpty()) {

			for (File file : this.deferredFiles) {

				try (final Tx tx = app.tx(true, false, false)) {

					// set properties from files.json
					final PropertyMap fileProperties = getPropertiesForFileOrFolder(file.getPath());

					final PropertyKey<Map<String, String>> sourcesPropertyKey = new GenericProperty("minificationSources");
					Map<String, String> sourcesConfig = fileProperties.get(sourcesPropertyKey);
					fileProperties.remove(sourcesPropertyKey);

					file.unlockSystemPropertiesOnce();
					file.setProperties(securityContext, fileProperties);

					for (String positionString : sourcesConfig.keySet()) {

						final Integer position    = Integer.parseInt(positionString);
						final String sourcePath   = sourcesConfig.get(positionString);
						final AbstractFile source = FileHelper.getFileByAbsolutePath(securityContext, sourcePath);

						if (source != null) {

							app.create(app.get(AbstractMinifiedFile.class, file.getUuid()), (File)source, MinificationSource.class, new PropertyMap(MinificationSource.position, position));

						} else {
							logger.warn("Source file {} for minified file {} at position {} not found - please verify that it is included in the export", sourcePath, file.getPath(), positionString);
						}
					}

					tx.success();

				} catch (FrameworkException fxe) {

				}
			}
		}
	}

	// ----- private methods -----
	private void createFolder(final Path file) {

		try (final Tx tx = app.tx(true, false, false)) {

			// create folder
			final Folder folder = createFolders(basePath.relativize(file));
			if (folder != null) {

				// set properties from files.json
				final PropertyMap properties = getPropertiesForFileOrFolder(folder.getPath());
				if (properties != null) {

					folder.setProperties(securityContext, properties);
				}
			}

			tx.success();

		} catch (Exception ex) {
			logger.error("Error occured while importing folder " + file, ex);
		}
	}

	private void createFile(final Path path, final String fileName) throws IOException {

		String newFileUuid = null;
		try (final Tx tx = app.tx(true, false, false)) {

			final Path parentPath    = basePath.relativize(path).getParent();
			final Folder parent      = createFolders(parentPath);
			final String fullPath    = (parentPath != null ? "/" + parentPath.toString() : "") + "/" + fileName;
			boolean skipFile         = false;

			// load properties from files.json
			final PropertyMap fileProperties = getPropertiesForFileOrFolder(fullPath);
			if (fileProperties == null) {

				logger.info("Ignoring {} (not in files.json)", fullPath);

			} else {

				File file = app.nodeQuery(File.class).and(File.parent, parent).and(File.name, fileName).getFirst();

				if (file != null) {

					final Long checksumOfExistingFile = file.getChecksum();
					final Long checksumOfNewFile      = FileHelper.getChecksum(path.toFile());

					if (checksumOfExistingFile != null && checksumOfNewFile != null && checksumOfExistingFile.equals(checksumOfNewFile)) {

						skipFile = true;

					} else {

						// remove existing file first!
						app.delete(file);
					}
				}

				if (!skipFile) {

					logger.info("Importing {}...", fullPath);

					try (final FileInputStream fis = new FileInputStream(path.toFile())) {

						// create file in folder structure
						file                     = FileHelper.createFile(securityContext, fis, null, File.class, fileName);
						final String contentType = file.getContentType();

						final PropertyMap changedProperties = new PropertyMap();

						// modify file type according to content
						if (StringUtils.startsWith(contentType, "image") || ImageHelper.isImageType(file.getProperty(name))) {

							changedProperties.put(NodeInterface.type, Image.class.getSimpleName());
						}

						// move file to folder
						file.setProperty(File.parent, parent);

						file.unlockSystemPropertiesOnce();
						file.setProperties(securityContext, changedProperties);

						newFileUuid = file.getUuid();
					}
				}

				if (file != null) {

					if (fileProperties.containsKey(AbstractMinifiedFile.minificationSources)) {
						deferredFiles.add(file);
					} else {
						file.unlockSystemPropertiesOnce();
						file.setProperties(securityContext, fileProperties);
					}

				}
			}

			tx.success();

		} catch (Exception ex) {
			logger.error("Error occured while importing file " + fileName, ex);
		}

		try (final Tx tx = app.tx(true, false, false)) {

			if (newFileUuid != null) {

				final File createdFile = app.get(File.class, newFileUuid);
				String type            = createdFile.getType();
				boolean isImage        = createdFile.getProperty(Image.isImage);
				boolean isThumbnail    = createdFile.getProperty(Image.isThumbnail);

				logger.debug("File {}: {}, isImage? {}, isThumbnail? {}", new Object[] { createdFile.getName(), type, isImage, isThumbnail});

				if (isImage) {

					try {
						ImageHelper.updateMetadata(createdFile);
						handleThumbnails((Image) createdFile);

					} catch (Throwable t) {
						logger.warn("Unable to update metadata: {}", t.getMessage());
					}
				}

			}

			tx.success();

		} catch (Exception ex) {
			logger.error("Error occured while importing file " + fileName, ex);
		}

	}

	private void handleThumbnails(final Image img) {


		if (img.getProperty(Image.isThumbnail)) {

			// thumbnail image
			if (img.getIncomingRelationship(Thumbnails.class) == null) {

				ImageHelper.findAndReconnectOriginalImage(img);
			}

		} else {

			// original image
			if (!img.getOutgoingRelationships(Thumbnails.class).iterator().hasNext()) {

				ImageHelper.findAndReconnectThumbnails(img);

			}
		}

	}

	private PropertyMap getPropertiesForFileOrFolder(final String path) throws FrameworkException {

		final Object data = config.get(path);
		if (data != null && data instanceof Map) {

			return PropertyMap.inputTypeToJavaType(SecurityContext.getSuperUserInstance(), AbstractFile.class, (Map<String, Object>)data);
		}

		return null;
	}

	private Folder createFolders(final Path folder) throws FrameworkException {

		if (folder != null) {

			final App app  = StructrApp.getInstance();
			Folder current = null;
			Folder parent  = null;

			for (final Iterator<Path> it = folder.iterator(); it.hasNext(); ) {

				final Path part   = it.next();
				final String name = part.toString();

				current = app.nodeQuery(Folder.class).andName(name).and(File.parent, parent).getFirst();
				if (current == null) {

					current = app.create(Folder.class,
						new NodeAttribute(AbstractNode.name, name),
						new NodeAttribute(Folder.parent, parent)
					);

					// set properties from files.json, but only when the folder is created
					final PropertyMap properties = getPropertiesForFileOrFolder(current.getPath());
					if (properties != null) {

						current.unlockSystemPropertiesOnce();
						current.setProperties(securityContext, properties);
					}
				}

				// make next folder child of new one
				parent = current;
			}

			return current;
		}

		return null;
	}
}
