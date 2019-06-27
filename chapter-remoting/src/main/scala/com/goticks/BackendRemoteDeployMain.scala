package com.goticks

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem

object BackendRemoteDeployMain extends App {
  // 读取配置文件
  val config = ConfigFactory.load("backend")
  // 创建一个backend actor环境
  val system = ActorSystem("backend", config)

  // 注意跟 BackendMain 对比，actor-BoxOffice并不由backend-actor-System来创建
  // 所以此处没有创建actor 的相关逻辑
  // actor-BoxOffice由 FrontendRemoteDeployMain根据配置来创建，同时将actor部署到配置文件配置的远端actor-System
  // 注意一点：如果远端actor-System还没启动，则部署肯定失败。
  // 在先启动后端再启动前端的情况下，应该能正常运行，但是后端若重启，若前端没有重启，则用的还是旧的actorRef，系统也不能继续正常运行
  // 要改变这一点应该加一个中间监控者，参考FrontendRemoteDeployWatchMain
}
