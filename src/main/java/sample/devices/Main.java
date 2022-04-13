package sample.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import akka.actor.AddressFromURIString;
import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Main entry point for the application.
 */
public class Main {

  public static void main(String[] args) throws Exception {
    List<Integer> seedNodePorts =
        ConfigFactory.load().getStringList("akka.cluster.seed-nodes")
            .stream()
            .map(AddressFromURIString::parse)
            .map(addr -> (Integer) addr.port().get()) // Missing Java getter for port, fixed in Akka 2.6.2
            .collect(Collectors.toList());

    // Either use a single port provided by the user
    // Or start each listed seed nodes port plus one node on a random port in this single JVM if the user
    // didn't provide args for the app
    // In a production application you wouldn't start multiple ActorSystem instances in the
    // same JVM, here we do it to simplify running a sample cluster from a single main method.
    List<Integer> ports = Arrays.stream(args).findFirst().map(str ->
        Collections.singletonList(Integer.parseInt(str))
    ).orElseGet(() -> {
      List<Integer> portsAndZero = new ArrayList<>(seedNodePorts);
      portsAndZero.add(0);
      return portsAndZero;
    });

    for (int port : ports) {
      final int httpPort;
      if (port > 0)
        httpPort = 10000 + port;  // offset from akka port
      else
        httpPort = 0; // let OS decide

      Config config = configWithPort(port);
      ActorSystem.create(DeviceGuardian.create(httpPort), "DeviceSim", config);
    }
  }

  private static Config configWithPort(int port) {
    return ConfigFactory.parseMap(
        Collections.singletonMap("akka.remote.artery.canonical.port", Integer.toString(port))
    ).withFallback(ConfigFactory.load());
  }
}