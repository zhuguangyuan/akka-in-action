package com.goticks

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * 启动后端的 actor-System 环境，并创建其上运行的 actor-boxOffice
  */
object BackendMain extends App with RequestTimeout {
  // 读取配置文件
  val config = ConfigFactory.load("backend")
  // 创建一个backend actor环境
  val system = ActorSystem("backend", config)

  // 注意这里提供了一个后端超时的配置时间，注入到BoxOffice.props 的implicit参数
  implicit val requestTimeout = configuredRequestTimeout(config)
  // 用backend环境创建一个BoxOffice.name的actor
  system.actorOf(BoxOffice.props, BoxOffice.name)
}
