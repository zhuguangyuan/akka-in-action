package com.goticks

import akka.actor.{ ActorSystem, ActorRef }
import akka.event.Logging

import com.typesafe.config.ConfigFactory

/**
  * 单节点部署方式 对应akka-in-action chapter-up-and-running中的情况
  * 加载本地配置信息，BoxOffice直接在本地创建
  */
object SingleNodeMain extends App
    with FrontendStartup {
  // 配置信息
  val config = ConfigFactory.load("singlenode") 
  // 注入Startup需要的actorSystem环境
  implicit val system = ActorSystem("singlenode", config) 

  val api = new RestApi() {
    val log = Logging(system.eventStream, "go-ticks")

    // 提供父类 RestApi 声明的implicit变量
    implicit val requestTimeout = configuredRequestTimeout(config)
    implicit def executionContext = system.dispatcher

    // 实现 RestApi中定义的方法，因为actor-BoxOffice也部署在同一个 actor-System,
    // 所以此处直接通过BoxOffice.props来创建即可
    def createBoxOffice: ActorRef =
      system.actorOf(BoxOffice.props, BoxOffice.name)
  }

  startup(api.routes)
}
