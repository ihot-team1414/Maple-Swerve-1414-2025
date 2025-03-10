package frc.robot.subsystems.drive.IO;

import static frc.robot.constants.DriveTrainConstants.*;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.Robot;
import frc.robot.subsystems.drive.OdometryThreadReal;
import frc.robot.subsystems.drive.SwerveDrive;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import org.littletonrobotics.junction.AutoLog;

public interface OdometryThread {
    class OdometryInput<T> {
        private final Supplier<T> supplier;
        private final Queue<T> queue;

        public OdometryInput(Supplier<T> signal) {
            this.supplier = signal;
            this.queue = new ArrayBlockingQueue<>(ODOMETRY_CACHE_CAPACITY);
        }

        public void cacheInputToQueue() {
            this.queue.offer(supplier.get());
        }
    }

    List<OdometryInput> registeredInputs = new ArrayList<>();
    List<BaseStatusSignal> registeredStatusSignals = new ArrayList<>();

    static <T> Queue<T> registerSignalSignal(StatusSignal<T> signal) {
        registeredStatusSignals.add(signal);
        return registerInput(signal.asSupplier());
    }

    static <T> Queue<T> registerInput(Supplier<T> supplier) {
        final OdometryInput odometryInput = new OdometryInput(supplier);
        registeredInputs.add(odometryInput);
        return odometryInput.queue;
    }

    static OdometryThread createInstance(SwerveDrive.DriveType type) {
        return switch (Robot.CURRENT_ROBOT_MODE) {
            case REAL -> new OdometryThreadReal(
                    type,
                    registeredInputs.toArray(new OdometryInput[0]),
                    registeredStatusSignals.toArray(new BaseStatusSignal[0]));
            case SIM -> new OdometryThreadSim();
            case REPLAY -> inputs -> {};
        };
    }

    @AutoLog
    class OdometryThreadInputs {
        public double[] measurementTimeStamps = new double[0];
    }

    void updateInputs(OdometryThreadInputs inputs);

    default void start() {}

    default void lockOdometry() {}

    default void unlockOdometry() {}

    final class OdometryThreadSim implements OdometryThread {
        @Override
        public void updateInputs(OdometryThreadInputs inputs) {
            inputs.measurementTimeStamps = new double[SIMULATION_TICKS_IN_1_PERIOD];
            final double robotStartingTimeStamps = Timer.getTimestamp(),
                    iterationPeriodSeconds = Robot.defaultPeriodSecs / SIMULATION_TICKS_IN_1_PERIOD;
            for (int i = 0; i < SIMULATION_TICKS_IN_1_PERIOD; i++)
                inputs.measurementTimeStamps[i] = robotStartingTimeStamps + i * iterationPeriodSeconds;
        }
    }
}
