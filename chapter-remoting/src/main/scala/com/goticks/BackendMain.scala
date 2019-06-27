package com.goticks

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * 启动后端的 actor-System 环境，并创建其上运行的 actor-boxOffice
  */
object BackendMain extends App with RequestTimeout {
  val config = ConfigFactory.load("backend")

  // 创建一个backend actor环境
  val system = ActorSystem("backend", config)
  // 注意这里提供了一个后端超时的配置时间，用于注入合适位置
  implicit val requestTimeout = configuredRequestTimeout(config)

  // 用backend环境创建一个BoxOffice.name的actor
  system.actorOf(BoxOffice.props, BoxOffice.name)
}
