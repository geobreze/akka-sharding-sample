package sample.devices;

import java.time.Duration;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.fasterxml.jackson.annotation.JsonCreator;

public class DeviceSim
    extends EventSourcedBehavior<DeviceSim.Command, DeviceSim.Event, DeviceSim.State> {

  public static final EntityTypeKey<DeviceSim.Command> TypeKey =
      EntityTypeKey.create(DeviceSim.Command.class, "DeviceSim");

  public static void initSharding(ActorSystem<?> system) {
    ClusterSharding.get(system)
        .init(Entity.of(TypeKey, entityContext -> DeviceSim.create(entityContext.getEntityId())));
  }

  /**
   * The state for the {@link DeviceSim} entity.
   */
  public static class State implements CborSerializable {
    private int state = 0; // 0 - stopped, 1 - running

    public State setState(int i) {
      this.state = i;
      return this;
    }

    public int getState() {
      return state;
    }
  }

  /**
   * This interface defines all the commands that the persistent actor supports.
   */
  public interface Command extends CborSerializable {
  }

  public static class Started implements CborSerializable {

    // required to make this event serializable
    private final int i = 0;

    @JsonCreator
    public Started() {}
  }

  public static class Stopped implements CborSerializable {

    // required to make this event serializable
    private final int i = 0;

    @JsonCreator
    public Stopped() {}
  }

  public static class Start implements Command {
    private final ActorRef<Started> replyTo;

    @JsonCreator
    public Start(ActorRef<Started> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static class Stop implements Command {
    private final ActorRef<Stopped> replyTo;

    @JsonCreator
    public Stop(ActorRef<Stopped> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static class Tick implements Command {

  }

  public interface Event extends CborSerializable {
  }

  public static class DeviceStarted implements Event {
    public final String deviceId;

    @JsonCreator
    public DeviceStarted(String deviceId) {
      this.deviceId = deviceId;
    }
  }

  public static class DeviceStopped implements Event {
    public final String deviceId;

    @JsonCreator
    public DeviceStopped(String deviceId) {
      this.deviceId = deviceId;
    }
  }

  public static Behavior<Command> create(String deviceId) {
    return Behaviors.setup(context -> {
      context.getSelf().tell(new Tick());
      return new DeviceSim(context, deviceId);
    });
  }

  private final String deviceId;
  private final ActorContext<Command> context;

  private DeviceSim(ActorContext<Command> context, String deviceId) {
    super(PersistenceId.of("Device", deviceId),
        SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1));
    this.context = context;
    this.deviceId = deviceId;
  }

  @Override
  public State emptyState() {
    return new State();
  }

  private final RunningHandler runningHandler = new RunningHandler();
  private final IdleHandler idleHandler = new IdleHandler();

  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    CommandHandlerBuilder<Command, Event, State> b =
        newCommandHandlerBuilder();

    b.forState(state -> state.getState() == 0)
        .onCommand(Tick.class, idleHandler::tick)
        .onCommand(Start.class, idleHandler::onStart)
        .onCommand(Stop.class, idleHandler::onStop);

    b.forState(state -> state.getState() == 1)
        .onCommand(Tick.class, runningHandler::tick)
        .onCommand(Start.class, runningHandler::onStart)
        .onCommand(Stop.class, runningHandler::onStop);

    return b.build();
  }

  private class IdleHandler {

    Effect<Event, State> tick(State state, Tick tick) {
      return Effect().none();
    }

    Effect<Event, State> onStart(State state, Start start) {
      context.getLog().info("Starting {}", deviceId);
      context.getSelf().tell(new Tick());
      return Effect().persist(new DeviceStarted(deviceId))
          .thenReply(start.replyTo, newState -> new Started());
    }

    Effect<Event, State> onStop(State state, Stop stop) {
      context.getLog().info("Already stopped {}", deviceId);
      return Effect().none();
    }
  }

  private class RunningHandler {

    Effect<Event, State> tick(State state, Tick tick) {
      context.getLog().info("Tick");

      context.getLog().info("Sending tick to kafka from {}", deviceId);

      context.scheduleOnce(Duration.ofSeconds(10), context.getSelf(), new Tick());

      return Effect().none();
    }

    Effect<Event, State> onStart(State state, Start start) {
      context.getLog().info("Already started {}", deviceId);
      return Effect().none().thenReply(start.replyTo, newState -> new Started());
    }

    Effect<Event, State> onStop(State state, Stop stop) {
      context.getLog().info("Stopping {}", deviceId);
      return Effect().persist(new DeviceStopped(deviceId))
          .thenReply(stop.replyTo, newState -> new Stopped());
    }
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(DeviceStarted.class, (state, event) -> {
          context.getLog().info("Changing state to running {}", deviceId);
          return state.setState(1);
        })
        .onEvent(DeviceStopped.class, (state, event) -> {
          context.getLog().info("Changing state to stopped {}", deviceId);
          return state.setState(0);
        })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    // enable snapshotting
    return RetentionCriteria.snapshotEvery(5, 3);
  }
}
