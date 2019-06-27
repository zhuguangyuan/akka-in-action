package com.goticks

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import com.typesafe.config.ConfigFactory

object FrontendRemoteDeployWatchMain extends App
    with FrontendStartup {
  val config = ConfigFactory.load("frontend-remote-deploy") 
  implicit val system = ActorSystem("frontend", config) 

  val api = new RestApi() {
    val log = Logging(system.eventStream, "frontend-remote-watch")

    implicit val requestTimeout = configuredRequestTimeout(config)
    implicit def executionContext = system.dispatcher

    /**
      * 加入中间组件 RemoteBoxOfficeForwarder 监控远端actor失效的情况
      * 重新获取actorRef并部署到远端
      * @return
      */
    def createBoxOffice: ActorRef = {
      system.actorOf(
        RemoteBoxOfficeForwarder.props, 
        RemoteBoxOfficeForwarder.name
      )
    }
  }

  startup(api.routes)
}
