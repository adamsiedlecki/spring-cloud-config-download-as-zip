/*
 * Copyright 2015-2019 the original author or authors.
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

import java.io.IOException;
import java.net.URL;

import com.amazonaws.services.s3.AmazonS3;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.OutputCaptureRule;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryTests;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 *
 */
public class GenericResourceRepositoryTests {

	@Rule
	public OutputCaptureRule output = new OutputCaptureRule();

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private GenericResourceRepository repository;

	private ConfigurableApplicationContext context;

	private NativeEnvironmentRepository nativeRepository;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Before
	public void init() {
		this.context = new SpringApplicationBuilder(NativeEnvironmentRepositoryTests.class).web(WebApplicationType.NONE)
				.run();
		this.nativeRepository = new NativeEnvironmentRepository(this.context.getEnvironment(),
				new NativeEnvironmentProperties());
		this.repository = new GenericResourceRepository(this.nativeRepository);
		this.repository.setResourceLoader(this.context);
		this.context.close();
	}

	@Test
	public void locateResource() {
		assertThat(this.repository.findOne("blah", "default", "master", "foo.properties")).isNotNull();
	}

	@Test
	public void locateProfiledResource() {
		assertThat(this.repository.findOne("blah", "local", "master", "foo.txt")).isNotNull();
	}

	@Test
	public void locateProfiledResourceWithPlaceholder() {
		this.nativeRepository.setSearchLocations("classpath:/test/{profile}");
		assertThat(this.repository.findOne("blah", "local", "master", "foo.txt")).isNotNull();
	}

	@Test(expected = NoSuchResourceException.class)
	public void locateMissingResource() {
		assertThat(this.repository.findOne("blah", "default", "master", "foo.txt")).isNotNull();
	}

	@Test
	public void invalidPath() {
		this.exception.expect(NoSuchResourceException.class);
		this.nativeRepository.setSearchLocations("file:./src/test/resources/test/{profile}");
		this.output.expect(containsString("Path contains \"../\" after call to StringUtils#cleanPath"));
		this.repository.findOne("blah", "local", "master", "..%2F..%2Fdata-jdbc.sql");
	}

	@Test
	public void invalidPathWithPreviousDirectory() {
		testInvalidPath("../");
	}

	@Test
	public void invalidPathWithPreviousDirectoryEncodedSlash() {
		testInvalidPath("..%2F");
	}

	@Test
	public void invalidPathWithPreviousDirectoryAllEncoded() {
		testInvalidPath("%2E%2E%2F");
	}

	@Test
	public void invalidPathEncodedSlash() {
		String file = System.getProperty("user.dir");
		file = file.replaceFirst("\\/", "%2f");
		file += "/src/test/resources/ssh/key";
		this.exception.expect(NoSuchResourceException.class);
		this.nativeRepository.setSearchLocations("file:./");
		this.output.expect(containsString("is neither under the current location"));
		this.repository.findOne("blah", "local", "master", file);
	}

	private void testInvalidPath(String label) {
		this.exception.expect(NoSuchResourceException.class);
		this.nativeRepository.setSearchLocations("file:./src/test/resources/test/local");
		this.output.expect(containsString("Location contains \"..\""));
		this.repository.findOne("blah", "local", label, "foo.properties");
	}

	@Test
	/**
	 * This test is mocking what happens when using Spring Cloud AWS ProtocolResolver to
	 * server text files from S3 buckets. Rather than put Spring Cloud AWS on the
	 * classpath we just mock the behavior. Specifically we are testing to make sure the
	 * encoding of the / in the path is taken into account in the
	 * GenericResourceRepository.
	 */
	public void tests3() throws IOException {
		AmazonS3 s3 = mock(AmazonS3.class);
		AwsS3EnvironmentRepository awsS3EnvironmentRepository = new AwsS3EnvironmentRepository(s3, "test",
				new ConfigServerProperties());
		GenericResourceRepository genericResourceRepository = new GenericResourceRepository(awsS3EnvironmentRepository);
		genericResourceRepository.setResourceLoader(new ResourceLoader() {
			@Override
			public Resource getResource(String location) {
				Resource relativeResource = mock(Resource.class);
				when(relativeResource.exists()).thenReturn(true);
				when(relativeResource.isReadable()).thenReturn(true);
				Resource resource = null;
				try {
					when(relativeResource.getURL()).thenReturn(new URL("https://us-east-1/test/main%2Fdata.json"));
					resource = mock(Resource.class);
					when(resource.getURL()).thenReturn(new URL("https://us-east-1/test"));
					when(resource.createRelative(eq("data.json"))).thenReturn(relativeResource);
				}
				catch (IOException e) {
					fail("Exception thrown", e);
				}
				return resource;
			}

			@Override
			public ClassLoader getClassLoader() {
				return null;
			}
		});

		Resource resource = genericResourceRepository.findOne("app", "default", "main", "data.json");
		assertThat(resource.getURL()).isEqualTo(new URL("https://us-east-1/test/main%2Fdata.json"));
	}

}
