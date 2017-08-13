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
package org.structr.files.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.ftpserver.ftplet.FtpFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.QueryResult;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.Folder;
import org.structr.web.entity.dom.Page;

/**
 *
 *
 */
public class StructrFtpFolder extends AbstractStructrFtpFile implements FtpFile {

	private static final Logger logger = LoggerFactory.getLogger(StructrFtpFolder.class.getName());

	public StructrFtpFolder(final SecurityContext securityContext, final Folder folder) {
		super(securityContext, folder);
	}

	@Override
	public boolean doesExist() {
		boolean exists = "/".equals(newPath) || super.doesExist();
		return exists;
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public boolean isFile() {
		return false;
	}

	@Override
	public int getLinkCount() {
		return 1;
	}

	@Override
	public long getLastModified() {

		try (Tx tx = StructrApp.getInstance(securityContext).tx()) {

			final Date date = structrFile.getProperty(Folder.lastModifiedDate);

			tx.success();

			return date.getTime();

		} catch (Exception ex) {
		}

		return 0L;
	}

	@Override
	public long getSize() {
		return listFiles().size();
	}

	@Override
	public List<FtpFile> listFiles() {

		final List<FtpFile> ftpFiles = new ArrayList();

		final App app = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			String requestedPath = getAbsolutePath();
			logger.debug("Children of {} requested", requestedPath);

			if ("/".equals(requestedPath)) {
				try {
					QueryResult<Folder> folders = app.nodeQuery(Folder.class).getResult();
					for (Folder f : folders) {

						if (f.getProperty(AbstractFile.hasParent)) {
							continue;
						}

						FtpFile ftpFile = new StructrFtpFolder(securityContext, f);
						logger.debug("Folder found: {}", ftpFile.getAbsolutePath());

						ftpFiles.add(ftpFile);

					}

					QueryResult<FileBase> files = app.nodeQuery(FileBase.class).getResult();
					for (FileBase f : files) {

						if (f.getProperty(AbstractFile.hasParent)) {
							continue;
						}

						logger.debug("Structr file found: {}", f);

						FtpFile ftpFile = new StructrFtpFile(securityContext, f);
						logger.debug("File found: {}", ftpFile.getAbsolutePath());

						ftpFiles.add(ftpFile);

					}

					QueryResult<Page> pages = app.nodeQuery(Page.class).getResult();
					for (Page p : pages) {

						logger.debug("Structr page found: {}", p);

						ftpFiles.add(new FtpFilePageWrapper(p));

					}

					return ftpFiles;

				} catch (FrameworkException ex) {
					logger.error("", ex);
				}

			}

			List<Folder> folders = ((Folder) structrFile).getProperty(Folder.folders);

			for (Folder f : folders) {

				FtpFile ftpFile = new StructrFtpFolder(securityContext, f);
				logger.debug("Subfolder found: {}", ftpFile.getAbsolutePath());

				ftpFiles.add(ftpFile);
			}

			List<FileBase> files = ((Folder) structrFile).getProperty(Folder.files);

			for (FileBase f : files) {

				FtpFile ftpFile = new StructrFtpFile(securityContext, f);
				logger.debug("File found: {}", ftpFile.getAbsolutePath());

				ftpFiles.add(ftpFile);
			}

			tx.success();

			return ftpFiles;

		} catch (FrameworkException fex) {
			logger.error("Error in listFiles()", fex);
		}

		return null;

	}

	@Override
	public boolean mkdir() {
		logger.error("Use FileOrFolder#createOutputStream() instead!");
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public OutputStream createOutputStream(final long l) throws IOException {
		logger.debug("createOutputStream()");
		throw new UnsupportedOperationException("Not supported.");
	}

	@Override
	public InputStream createInputStream(final long l) throws IOException {
		logger.debug("createInputStream()");
		throw new UnsupportedOperationException("Not supported.");
	}

	@Override
	public Object getPhysicalFile() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
