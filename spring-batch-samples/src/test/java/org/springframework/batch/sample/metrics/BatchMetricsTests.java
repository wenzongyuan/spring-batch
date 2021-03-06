/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.sample.metrics;

import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import org.junit.Test;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BatchMetricsTests {

	private static final int EXPECTED_SPRING_BATCH_METRICS = 6;

	@Test
	public void testBatchMetrics() throws Exception {
		// given
		ApplicationContext context = new AnnotationConfigApplicationContext(MyJobConfiguration.class);
		JobLauncher jobLauncher = context.getBean(JobLauncher.class);
		Job job = context.getBean(Job.class);

		// when
		JobExecution jobExecution = jobLauncher.run(job, new JobParameters());

		// then
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
		List<Meter> meters = Metrics.globalRegistry.getMeters();
		assertTrue(meters.size() >= EXPECTED_SPRING_BATCH_METRICS);

		try {
			Metrics.globalRegistry.get("spring.batch.job")
					.tag("name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.job " +
					"registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.job.active")
					.longTaskTimer();
		} catch (Exception e) {
			fail("There should be a meter of type LONG_TASK_TIMER named spring.batch.job.active" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.step")
					.tag("name", "step1")
					.tag("job.name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.step" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.step")
					.tag("name", "step2")
					.tag("job.name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.step" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.item.read")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.item.read" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.item.process")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.item.process" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.chunk.write")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.chunk.write" +
					" registered in the global registry: " + e.getMessage());
		}
	}

	@Configuration
	@EnableBatchProcessing
	static class MyJobConfiguration {

		private JobBuilderFactory jobBuilderFactory;
		private StepBuilderFactory stepBuilderFactory;

		public MyJobConfiguration(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory) {
			this.jobBuilderFactory = jobBuilderFactory;
			this.stepBuilderFactory = stepBuilderFactory;
		}

		@Bean
		public Step step1() {
			return stepBuilderFactory.get("step1")
					.tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED)
					.build();
		}

		@Bean
		public ItemReader<Integer> itemReader() {
			return new ListItemReader<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
		}

		@Bean
		public ItemWriter<Integer> itemWriter() {
			return items -> {
				for (Integer item : items) {
					System.out.println("item = " + item);
				}
			};
		}

		@Bean
		public Step step2() {
			return stepBuilderFactory.get("step2")
					.<Integer, Integer>chunk(5)
					.reader(itemReader())
					.writer(itemWriter())
					.build();
		}

		@Bean
		public Job job() {
			return jobBuilderFactory.get("job")
					.start(step1())
					.next(step2())
					.build();
		}
	}
}
