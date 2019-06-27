package com.goticks

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.event.Logging
import com.typesafe.config.ConfigFactory

/**
  * 创建前端的 actor-System环境并 实现RestApi定义的 创建actor-RemoteLookupProxy的方法
  * 然后启动接受REST的http服务
  */
object FrontendMain extends App with FrontendStartup {
  val config = ConfigFactory.load("frontend")

  // 创建一个 frontend 的actor环境
  implicit val system = ActorSystem("frontend", config) 

  val api = new RestApi() {
    val log = Logging(system.eventStream, "frontend")
    implicit val requestTimeout = configuredRequestTimeout(config)
    implicit def executionContext = system.dispatcher
    
    private def createPath(): String = {
      val config = ConfigFactory.load("frontend").getConfig("backend")
      val host = config.getString("host")
      val port = config.getInt("port")
      val protocol = config.getString("protocol")
      val systemName = config.getString("system")
      val actorName = config.getString("actor")
      s"$protocol://$systemName@$host:$port/$actorName"
    }

    // 实现 RestApi中定义的方法
    def createBoxOffice: ActorRef = {
      val path = createPath()
      system.actorOf(Props(new RemoteLookupProxy(path)), "lookupBoxOffice")
    }
  }

  // 启动http服务
  startup(api.routes)
}
