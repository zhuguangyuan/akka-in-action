package com.goticks

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.event.Logging

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route

import akka.stream.ActorMaterializer

/**
  * 这个类继承了 RequestTimeout,用在前端环境中,增加了一个绑定对外提供服务的ip:host
  * backend 不用对外提供服务，所以不用继承此类，直接继承RequestTimeout用于设定超时时间即可
  * 前后端node 通信的remote模块使用akka-remote,只要在backend.conf/frontend.conf中配置remote模块即可自动绑定
  */
trait FrontendStartup extends RequestTimeout {
  /**
    * 启动http 服务
    * @param api Route
    * @param system implicit修饰，注意如果不提供则编译器从implicit环境中查找并注入
    */
  def startup(api: Route)(implicit system: ActorSystem) = {
    val host = system.settings.config.getString("http.host") // Gets the host and a port from the configuration
    val port = system.settings.config.getInt("http.port")
    startHttpServer(api, host, port)
  }

  /**
    * 启动HTTP服务
    * @param api
    * @param host
    * @param port
    * @param system 如果不显式提供则靠外部注入
    */
  private def startHttpServer(api: Route, host: String, port: Int)
      (implicit system: ActorSystem) = {
    implicit val ec = system.dispatcher  //bindAndHandle requires an implicit ExecutionContext
    implicit val materializer = ActorMaterializer()
    val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(api, host, port) //Starts the HTTP server
   
    val log = Logging(system.eventStream, "go-ticks")
    bindingFuture.map { serverBinding =>
      log.info(s"RestApi bound to ${serverBinding.localAddress} ")
    }.onFailure { 
      case ex: Exception =>
        log.error(ex, "Failed to bind to {}:{}!", host, port)
        system.terminate()
    }
  }
}
