package com.goticks

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import com.typesafe.config.ConfigFactory

object FrontendRemoteDeployMain extends App
    with FrontendStartup {
  val config = ConfigFactory.load("frontend-remote-deploy") 
  implicit val system = ActorSystem("frontend", config) 

  val api = new RestApi() {
    val log = Logging(system.eventStream, "frontend-remote")

    implicit val requestTimeout = configuredRequestTimeout(config)
    implicit def executionContext = system.dispatcher

    // 直接读取配置文件创建 ActorRef，但是注意，配置文件中将实际的actor部署在远端actor-System环境
    // 此处没有监控，所以不知部署是否成功以及重启时远端是否正确
    def createBoxOffice: ActorRef =
      system.actorOf(BoxOffice.props, BoxOffice.name)
  }

  startup(api.routes)
}
