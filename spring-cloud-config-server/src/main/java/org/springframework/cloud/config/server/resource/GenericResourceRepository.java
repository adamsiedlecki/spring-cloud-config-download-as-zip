/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.cloud.config.server.support.PathUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link ResourceRepository} backed by a {@link SearchPathLocator}.
 *
 * @author Dave Syer
 */
public class GenericResourceRepository implements ResourceRepository, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	private SearchPathLocator service;

	private ConfigServerProperties properties;

	public GenericResourceRepository(SearchPathLocator service) {
		this.service = service;
	}

	public GenericResourceRepository(SearchPathLocator service, ConfigServerProperties properties) {
		this(service);
		this.properties = properties;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public Resource findOne(String application, String profile, String label, String path) {
		return findOne(application, profile, label, path, false);
	}

	@Override
	public synchronized Resource findOne(String application, String profile, String label, String path,
			boolean packInZip) {

		if (!StringUtils.hasText(path)) {
			throw new NoSuchResourceException("Not found: " + path);
		}

		String[] locations = this.service.getLocations(application, profile, label).getLocations();
		if (!ObjectUtils.isEmpty(properties) && properties.isReverseLocationOrder()) {
			Collections.reverse(Arrays.asList(locations));
		}
		ArrayList<Resource> locationResources = new ArrayList<>();
		for (String location : locations) {
			if (!PathUtils.isInvalidEncodedLocation(location)) {
				locationResources.add(this.resourceLoader.getResource(location.replaceFirst("optional:", "")));
			}
		}

		try {
			for (Resource location : locationResources) {
				for (String local : getProfilePaths(profile, path)) {
					if (!PathUtils.isInvalidPath(local) && !PathUtils.isInvalidEncodedPath(local)) {
						Resource file = location.createRelative(local);
						if (packInZip) {
							return new ByteArrayResource(packInZip(file));
						}
						else {
							return file;
						}
					}
				}
			}
		}
		catch (IOException e) {
			throw new NoSuchResourceException("Error : " + path + ". (" + e.getMessage() + ")");
		}
		throw new NoSuchResourceException("Not found: " + path);
	}

	private byte[] packInZip(Resource resource) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			ZipOutputStream zipOut = new ZipOutputStream(bos);
			File file = resource.getFile();
			if (!file.isDirectory()) {
				throw new NoSuchResourceException("Not a directory: " + resource.getURL());
			}

			List<Path> paths = Files.walk(file.toPath(), 10).filter(p -> !new File(p.toUri()).isDirectory())
					.collect(Collectors.toList());
			for (Path path : paths) {
				ZipEntry zipEntry = new ZipEntry(path.getFileName().toString());
				zipOut.putNextEntry(zipEntry);
				InputStream ris = Files.newInputStream(path);

				byte[] bytes = new byte[1024];
				int length;
				while ((length = ris.read(bytes)) >= 0) {
					zipOut.write(bytes, 0, length);
				}
				ris.close();

			}
			zipOut.close();
			return bos.toByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Collection<String> getProfilePaths(String profiles, String path) {
		Set<String> paths = new LinkedHashSet<>();
		for (String profile : StringUtils.commaDelimitedListToSet(profiles)) {
			if (!StringUtils.hasText(profile) || "default".equals(profile)) {
				paths.add(path);
			}
			else {
				String ext = StringUtils.getFilenameExtension(path);
				String file = path;
				if (ext != null) {
					ext = "." + ext;
					file = StringUtils.stripFilenameExtension(path);
				}
				else {
					ext = "";
				}
				paths.add(file + "-" + profile + ext);
			}
		}
		paths.add(path);
		return paths;
	}

}
