/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.front50.redis
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ RedisTestHelper.redisUnavailable() })
@WebAppConfiguration
@ContextConfiguration(classes = [RedisSetup])
class RedisPipelineStrategyDAOSpec extends Specification {

  @Configuration
  @Import(RedisConfig)
  static class RedisSetup {}

  @Autowired
  RedisPipelineStrategyDAO redisPipelineStrategyDAO

  void setupSpec() {
    System.setProperty('spinnaker.redis.enabled', 'true')
  }

  void setup() {
    deleteAll()
  }

  def "should support standard create/refresh/findAll/delete behaviors"() {
    given:
    def pipeline = new Pipeline([
        application       : "app1",
        name: "pipeline1"
    ])
    def newPipeline = redisPipelineStrategyDAO.create("app1", pipeline)

    when:
    def foundPipelineId = redisPipelineStrategyDAO.getPipelineId("app1", "pipeline1")
    redisPipelineStrategyDAO.findById(foundPipelineId).id == foundPipelineId

    then:
    foundPipelineId == newPipeline.id

    when:
    def findPipelines = redisPipelineStrategyDAO.all()

    then:
    findPipelines.size() == 1
    findPipelines[0].id == pipeline.id
    findPipelines[0].createTs == pipeline.createTs
    findPipelines[0].email == pipeline.email
    findPipelines[0].lastModified == pipeline.lastModified
    findPipelines[0].updateTs == pipeline.updateTs

    when:
    def pipelinesByApplication = redisPipelineStrategyDAO.getPipelinesByApplication('app1')

    then:
    pipelinesByApplication.size() == 1
    pipelinesByApplication[0].id == pipeline.id
    pipelinesByApplication[0].createTs == pipeline.createTs
    pipelinesByApplication[0].email == pipeline.email
    pipelinesByApplication[0].lastModified == pipeline.lastModified
    pipelinesByApplication[0].updateTs == pipeline.updateTs

    when:
    def updatedPipeline = pipelinesByApplication[0]
    updatedPipeline.foo = "test"
    redisPipelineStrategyDAO.update(foundPipelineId, updatedPipeline)

    then:
    redisPipelineStrategyDAO.findById(foundPipelineId).foo == updatedPipeline.foo

    when:
    redisPipelineStrategyDAO.delete(foundPipelineId)
    redisPipelineStrategyDAO.findById(foundPipelineId)

    then:
    thrown(NotFoundException)

    then:
    redisPipelineStrategyDAO.all().isEmpty()

    when:
    redisPipelineStrategyDAO.bulkImport([
        new Pipeline([
            name       : "app1",
            email: "greg@example.com",
            application: 'app'
        ]),
        new Pipeline([
            name       : "app2",
            email: "mark@example.com",
            application: 'app'
        ])
    ])

    then:
    def appPipelines = redisPipelineStrategyDAO.all()
    appPipelines.size() == 2
    appPipelines.collect {it.name}.containsAll(['app1', 'app2'])
    appPipelines.collect {it.email}.containsAll(['greg@example.com', 'mark@example.com'])

    expect:
    redisPipelineStrategyDAO.healthy == true
  }

  def "should report failing redis connection as not healthy"() {
    given:
    redisPipelineStrategyDAO.redisTemplate.connectionFactory = Mock(RedisConnectionFactory)

    when:
    def healthy = redisPipelineStrategyDAO.healthy

    then:
    healthy == false

    1 * redisPipelineStrategyDAO.redisTemplate.connectionFactory.getConnection() >> { throw new RuntimeException('Failed') }
    0 * redisPipelineStrategyDAO.redisTemplate.connectionFactory._
  }

  void deleteAll() {
    redisPipelineStrategyDAO.redisTemplate.delete(RedisPipelineStrategyDAO.BOOK_KEEPING_KEY)
  }

}