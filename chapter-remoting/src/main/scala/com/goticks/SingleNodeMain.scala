package com.goticks

import akka.actor.{ ActorSystem, ActorRef }
import akka.event.Logging

import com.typesafe.config.ConfigFactory

object SingleNodeMain extends App
    with FrontendStartup {
  // 配置信息
  val config = ConfigFactory.load("singlenode") 
  // 注入Startup需要的actorSystem环境
  implicit val system = ActorSystem("singlenode", config) 

  val api = new RestApi() {
    val log = Logging(system.eventStream, "go-ticks")
    implicit val requestTimeout = configuredRequestTimeout(config)
    implicit def executionContext = system.dispatcher
    def createBoxOffice: ActorRef = system.actorOf(BoxOffice.props, BoxOffice.name)
  }

  startup(api.routes)
}
