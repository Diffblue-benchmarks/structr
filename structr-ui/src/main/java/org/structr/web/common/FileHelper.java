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
package org.structr.web.common;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.property.PropertyMap;
import org.structr.util.Base64;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.File;
import org.structr.web.entity.Folder;

/**
 * File utility class.
 *
 */
public class FileHelper {

	private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";
	private static final Logger logger = LoggerFactory.getLogger(FileHelper.class.getName());
	private static final MimetypesFileTypeMap mimeTypeMap = new MimetypesFileTypeMap(FileHelper.class.getResourceAsStream("/mime.types"));

	//~--- methods --------------------------------------------------------
	/**
	 * Transform an existing file into the target class.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param uuid
	 * @param fileType
	 * @return transformed file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.File> T transformFile(final SecurityContext securityContext, final String uuid, final Class<T> fileType) throws FrameworkException, IOException {

		AbstractFile existingFile = getFileByUuid(securityContext, uuid);

		if (existingFile != null) {

			existingFile.unlockSystemPropertiesOnce();
			existingFile.setProperties(securityContext, new PropertyMap(AbstractNode.type, fileType == null ? File.class.getSimpleName() : fileType.getSimpleName()));

			existingFile = getFileByUuid(securityContext, uuid);

			return (T)(fileType != null ? fileType.cast(existingFile) : (File) existingFile);
		}

		return null;
	}

	/**
	 * Create a new image node from image data encoded in base64 format.
	 *
	 * If the given string is an uuid of an existing file, transform it into
	 * the target class.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param rawData
	 * @param t defaults to File.class if null
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends File> T createFileBase64(final SecurityContext securityContext, final String rawData, final Class<T> t) throws FrameworkException, IOException {

		Base64URIData uriData = new Base64URIData(rawData);

		return createFile(securityContext, uriData.getBinaryData(), uriData.getContentType(), t);

	}

	/**
	 * Create a new file node from the given input stream
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileStream
	 * @param contentType
	 * @param fileType defaults to File.class if null
	 * @param name
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends File> T createFile(final SecurityContext securityContext, final InputStream fileStream, final String contentType, final Class<T> fileType, final String name)
		throws FrameworkException, IOException {

		final byte[] data = IOUtils.toByteArray(fileStream);
		return createFile(securityContext, data, contentType, fileType, name);

	}

	/**
	 * Create a new file node from the given byte array
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileData
	 * @param contentType if null, try to auto-detect content type
	 * @param t
	 * @param name
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.File> T createFile(final SecurityContext securityContext, final byte[] fileData, final String contentType, final Class<T> t, final String name)
		throws FrameworkException, IOException {

		PropertyMap props = new PropertyMap();

		props.put(AbstractNode.name, name);

		T newFile = (T) StructrApp.getInstance(securityContext).create(t, props);

		setFileData(newFile, fileData, contentType);

		// schedule indexing
		newFile.notifyUploadCompletion();

		return newFile;
	}

	/**
	 * Create a new file node from the given byte array
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileData
	 * @param contentType
	 * @param t defaults to File.class if null
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.File> T createFile(final SecurityContext securityContext, final byte[] fileData, final String contentType, final Class<T> t)
		throws FrameworkException, IOException {

		return createFile(securityContext, fileData, contentType, t, null);

	}

	/**
	 * Decodes base64-encoded raw data into binary data and writes it to the
	 * given file.
	 *
	 * @param file
	 * @param rawData
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void decodeAndSetFileData(final org.structr.web.entity.File file, final String rawData) throws FrameworkException, IOException {

		Base64URIData uriData = new Base64URIData(rawData);
		setFileData(file, uriData.getBinaryData(), uriData.getContentType());
	}

	/**
	 * Write image data to the given file node and set checksum and size.
	 *
	 * @param file
	 * @param fileData
	 * @param contentType if null, try to auto-detect content type
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void setFileData(final File file, final byte[] fileData, final String contentType) throws FrameworkException, IOException {

		FileHelper.writeToFile(file, fileData);

		final PropertyMap map = new PropertyMap();

		map.put(File.contentType, contentType != null ? contentType : getContentMimeType(file));
		map.put(File.checksum, FileHelper.getChecksum(file));
		map.put(File.size, FileHelper.getSize(file));
		map.put(File.version, 1);

		file.setProperties(file.getSecurityContext(), map);
	}

	/**
	 * Update checksum, content type, size and additional properties of the given file
	 *
	 * @param file the file
	 * @param map  additional properties
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final File file, final PropertyMap map) throws FrameworkException {

		// Don't overwrite existing MIME type
		if (StringUtils.isBlank(file.getContentType())) {

			try {

				map.put(File.contentType, getContentMimeType(file));

			} catch (IOException ex) {
				logger.debug("Unable to detect content MIME type", ex);
			}
		}

		map.put(File.checksum, FileHelper.getChecksum(file));

		long fileSize = FileHelper.getSize(file);
		if (fileSize > 0) {

			map.put(File.size, fileSize);
		}

		file.unlockSystemPropertiesOnce();
		file.setProperties(file.getSecurityContext(), map);
	}

	/**
	 * Update checksum content type and size of the given file
	 *
	 * @param file the file
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final File file) throws FrameworkException {

		updateMetadata(file, new PropertyMap());
	}

	//~--- get methods ----------------------------------------------------
	public static String getBase64String(final File file) {

		try {

			final InputStream is = file.getInputStream();
			if (is != null) {

				return Base64.encodeToString(IOUtils.toByteArray(is), false);
			}

		} catch (IOException ex) {
			logger.error("Could not get base64 string from file ", ex);
		}

		return null;
	}

	//~--- inner classes --------------------------------------------------
	public static class Base64URIData {

		private String contentType = null;
		private String data = null;

		//~--- constructors -------------------------------------------
		public Base64URIData(final String rawData) {

			if (rawData.contains(",")) {

				String[] parts = StringUtils.split(rawData, ",");
				if (parts.length == 2) {

					contentType = StringUtils.substringBetween(parts[0], "data:", ";base64");
					data        = parts[1];

				}

			} else {

				data = rawData;
			}

		}

		//~--- get methods --------------------------------------------
		public String getContentType() {

			return contentType;

		}

		public String getData() {

			return data;

		}

		public byte[] getBinaryData() {

			return Base64.decode(data);

		}

	}

	/**
	 * Write binary data to a file and reference the file on disk at the
	 * given file node
	 *
	 * @param fileNode
	 * @param inStream
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void writeToFile(final File fileNode, final InputStream inStream) throws FrameworkException, IOException {
		writeToFile(fileNode, IOUtils.toByteArray(inStream));
	}

	/**
	 * Write binary data to a file and reference the file on disk at the
	 * given file node
	 *
	 * @param fileNode
	 * @param data
	 * @throws FrameworkException
	 * @throws IOException
	 * @return the file on disk
	 */
	public static java.io.File writeToFile(final File fileNode, final byte[] data) throws FrameworkException, IOException {

		final PropertyMap properties = new PropertyMap();

		String id = fileNode.getProperty(GraphObject.id);
		if (id == null) {

			final String newUuid = UUID.randomUUID().toString().replaceAll("[\\-]+", "");
			id = newUuid;

			fileNode.unlockSystemPropertiesOnce();
			properties.put(GraphObject.id, newUuid);
		}

		properties.put(File.relativeFilePath, File.getDirectoryPath(id) + "/" + id);

		fileNode.unlockSystemPropertiesOnce();
		fileNode.setProperties(fileNode.getSecurityContext(), properties);

		final String filesPath = Settings.FilesPath.getValue();

		final java.io.File fileOnDisk = new java.io.File(filesPath + "/" + fileNode.getRelativeFilePath());

		fileOnDisk.getParentFile().mkdirs();
		FileUtils.writeByteArrayToFile(fileOnDisk, data);

		return fileOnDisk;

	}

	public static java.io.File getFile(final File file) {
		return new java.io.File(getFilePath(file.getRelativeFilePath()));
	}

	public static Path getPath(final File file) {
		return Paths.get(getFilePath(file.getRelativeFilePath()));
	}

	/**
	 * Return mime type of given file
	 *
	 * @param file
	 * @return content type
	 * @throws java.io.IOException
	 */
	public static String getContentMimeType(final File file) throws IOException {
		return getContentMimeType(file.getFileOnDisk(), file.getProperty(AbstractNode.name));
	}

	/**
	 * Return mime type of given file
	 *
	 * @param file
	 * @param name
	 * @return content type
	 * @throws java.io.IOException
	 */
	public static String getContentMimeType(final java.io.File file, final String name) throws IOException {

		String mimeType;

		// try name first, if not null
		if (name != null) {
			mimeType = mimeTypeMap.getContentType(name);
			if (mimeType != null && !UNKNOWN_MIME_TYPE.equals(mimeType)) {
				return mimeType;
			}
		}

		final MediaType mediaType = new DefaultDetector().detect(new BufferedInputStream(new FileInputStream(file)), new Metadata());

		mimeType = mediaType.toString();

//		// then file content
//		mimeType = Files.probeContentType(file.toPath());
//		if (mimeType != null && !UNKNOWN_MIME_TYPE.equals(mimeType)) {
//
//			return mimeType;
//		}
//
//		// fallback: jmimemagic
//		try {
//			final MagicMatch match = Magic.getMagicMatch(file, false, true);
//			if (match != null) {
//
//				return match.getMimeType();
//			}
//
//		} catch (MagicParseException | MagicMatchNotFoundException | MagicException ignore) {
//			// mlogger.warn("", ex);
//		}

		if (mimeType != null) {
			return mimeType;
		}

		// no success :(
		return UNKNOWN_MIME_TYPE;
	}

	/**
	 * Calculate CRC32 checksum of given file
	 *
	 * @param file
	 * @return checksum
	 */
	public static Long getChecksum(final File file) {

		String relativeFilePath = file.getRelativeFilePath();

		if (relativeFilePath != null) {

			try {

				String filePath = getFilePath(relativeFilePath);

				return getChecksum(new java.io.File(filePath));

			} catch (IOException ex) {

				logger.warn("Could not calculate checksum of file {}: {}", relativeFilePath, ex.getMessage());
			}
		}

		return null;
	}

	public static Long getChecksum(final java.io.File fileOnDisk) throws IOException {
		return FileUtils.checksumCRC32(fileOnDisk);
	}

	/**
	 * Return size of file on disk, or -1 if not possible
	 *
	 * @param file
	 * @return size
	 */
	public static long getSize(final File file) {

		String path = file.getRelativeFilePath();

		if (path != null) {

			String filePath = getFilePath(path);

			try {

				java.io.File fileOnDisk = new java.io.File(filePath);
				long fileSize = fileOnDisk.length();

				logger.debug("File size of node {} ({}): {}", new Object[]{file.getUuid(), filePath, fileSize});

				return fileSize;

			} catch (Exception ex) {

				logger.warn("Could not calculate file size of file {}: {}", filePath, ex);

			}

		}

		return -1;

	}

	/**
	 * Find a file by its absolute ancestor path.
	 *
	 * File may not be hidden or deleted.
	 *
	 * @param securityContext
	 * @param absolutePath
	 * @return file
	 */
	public static AbstractFile getFileByAbsolutePath(final SecurityContext securityContext, final String absolutePath) {

		try {

			return StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).and(AbstractFile.path, absolutePath).getFirst();

		} catch (FrameworkException ex) {
			logger.warn("File not found: {}", new Object[] { absolutePath });
		}

		return null;
	}

	public static AbstractFile getFileByUuid(final SecurityContext securityContext, final String uuid) {

		logger.debug("Search for file with uuid: {}", uuid);

		try {
			return StructrApp.getInstance(securityContext).get(AbstractFile.class, uuid);

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file by UUID {}: {}", uuid, fex.getMessage());
		}

		return null;
	}

	public static AbstractFile getFirstFileByName(final SecurityContext securityContext, final String name) {

		logger.debug("Search for file with name: {}", name);

		try {
			return StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).andName(name).getFirst();

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file for name {}: {}", name, fex.getMessage());
		}

		return null;
	}

	/**
	 * Find the first file with given name on root level (without parent folder).
	 *
	 * @param securityContext
	 * @param name
	 * @return file
	 */
	public static AbstractFile getFirstRootFileByName(final SecurityContext securityContext, final String name) {

		logger.debug("Search for file with name: {}", name);

		try {
			final List<AbstractFile> files = StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).andName(name).getAsList();

			for (final AbstractFile file : files) {

				if (file.getProperty(AbstractFile.parent) == null) {
					return file;
				}

			}

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file for name {}: {}", name, fex.getMessage());
		}

		return null;
	}

	/**
	 * Return the virtual folder path of any
	 * {@link File} or
	 * {@link org.structr.web.entity.Folder}
	 *
	 * @param file
	 * @return path
	 */
	public static String getFolderPath(final AbstractFile file) {

		LinkedTreeNode parentFolder = file.getProperty(AbstractFile.parent);

		String folderPath = file.getProperty(AbstractFile.name);

		if (folderPath == null) {
			folderPath = file.getProperty(GraphObject.id);
		}

		while (parentFolder != null) {
			folderPath = parentFolder.getName().concat("/").concat(folderPath);
			parentFolder = parentFolder.getProperty(AbstractFile.parent);
		}

		return "/".concat(folderPath);
	}

	public static String getFilePath(final String... pathParts) {

		final String filePath          = Settings.FilesPath.getValue();
		final StringBuilder returnPath = new StringBuilder();

		returnPath.append(filePath);
		returnPath.append(filePath.endsWith("/") ? "" : "/");

		for (String pathPart : pathParts) {
			returnPath.append(pathPart);
		}

		return returnPath.toString();
	}

	/**
	 * Create one folder per path item and return the last folder.
	 *
	 * F.e.: /a/b/c => Folder["name":"a"] --HAS_CHILD--> Folder["name":"b"]
	 * --HAS_CHILD--> Folder["name":"c"], returns Folder["name":"c"]
	 *
	 * @param securityContext
	 * @param path
	 * @return folder
	 * @throws FrameworkException
	 */
	public static Folder createFolderPath(final SecurityContext securityContext, final String path) throws FrameworkException {

		final App app = StructrApp.getInstance(securityContext);

		if (path == null) {

			return null;
		}

		Folder folder = (Folder) FileHelper.getFileByAbsolutePath(securityContext, path);

		if (folder != null) {
			return folder;
		}

		String[] parts = PathHelper.getParts(path);
		String partialPath = "";

		for (String part : parts) {

			// ignore ".." and "." in paths
			if ("..".equals(part) || ".".equals(part)) {
				continue;
			}

			Folder parent = folder;

			partialPath += PathHelper.PATH_SEP + part;
			folder = (Folder) FileHelper.getFileByAbsolutePath(securityContext, partialPath);

			if (folder == null) {

				folder = app.create(Folder.class, part);

			}

			if (parent != null) {

				folder.setProperties(securityContext, new PropertyMap(AbstractFile.parent, parent));

			}
		}

		return folder;

	}

	public static String getDateString() {
		return new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
	}
}
