/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(CompositeEnvironmentRepository.class)
public class CompositeConfiguration {

	@Bean
	@Primary
	@ConditionalOnBean(SearchPathLocator.class)
	public SearchPathCompositeEnvironmentRepository searchPathCompositeEnvironmentRepository(
			@Autowired(required = false) List<EnvironmentRepository> environmentRepos,
			ConfigServerProperties properties) {
		environmentRepos = environmentRepos != null ? environmentRepos : new ArrayList<>();
		return new SearchPathCompositeEnvironmentRepository(environmentRepos, properties.isFailOnCompositeError());
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean(SearchPathLocator.class)
	public CompositeEnvironmentRepository compositeEnvironmentRepository(
			@Autowired(required = false) List<EnvironmentRepository> environmentRepos,
			ConfigServerProperties properties) {
		environmentRepos = environmentRepos != null ? environmentRepos : new ArrayList<>();
		return new CompositeEnvironmentRepository(environmentRepos, properties.isFailOnCompositeError());
	}

}
