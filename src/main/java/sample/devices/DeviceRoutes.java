package sample.devices;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.delete;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.post;
import static akka.http.javadsl.server.PathMatchers.segment;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

public class DeviceRoutes {

  private final ClusterSharding sharding;
  private final Duration timeout;

  public DeviceRoutes(ActorSystem<?> system) {
    sharding = ClusterSharding.get(system);
    timeout = system.settings().config().getDuration("devices.routes.ask-timeout");
  }

  private CompletionStage<DeviceSim.Started> start(String deviceId) {
    EntityRef<DeviceSim.Command> ref = sharding.entityRefFor(DeviceSim.TypeKey, deviceId);
    return ref.ask(replyTo -> new DeviceSim.Start(replyTo), timeout);
  }

  private CompletionStage<DeviceSim.Stopped> stop(String deviceId) {
    EntityRef<DeviceSim.Command> ref = sharding.entityRefFor(DeviceSim.TypeKey, deviceId);
    return ref.ask(replyTo -> new DeviceSim.Stop(replyTo), timeout);
  }

  public Route device() {
    return path(segment("device").slash().concat(segment()), deviceId ->
        concat(
            post(() ->
                onSuccess(start(deviceId), performed -> complete(StatusCodes.ACCEPTED, "started device " + deviceId))
            ),
            delete(() ->
                onSuccess(stop(deviceId), performed -> complete(StatusCodes.ACCEPTED, "stopped device " + deviceId))
            )
        )
    );
  }
}
