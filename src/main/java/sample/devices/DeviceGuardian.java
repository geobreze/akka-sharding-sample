package sample.devices;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

public class DeviceGuardian {

  public static Behavior<Void> create(int httpPort) {
    return Behaviors.setup(context -> {
      DeviceSim.initSharding(context.getSystem());

      DeviceRoutes routes = new DeviceRoutes(context.getSystem());
      HttpServer.start(routes.device(), httpPort, context.getSystem());

      return Behaviors.empty();
    });
  }

}
