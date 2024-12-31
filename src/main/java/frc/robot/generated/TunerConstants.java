package frc.robot.generated;

import static edu.wpi.first.units.Units.*;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
import com.ctre.phoenix6.configs.Pigeon2Configuration;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.signals.StaticFeedforwardSignValue;
import com.ctre.phoenix6.swerve.SwerveDrivetrainConstants;
import com.ctre.phoenix6.swerve.SwerveModuleConstants;
import com.ctre.phoenix6.swerve.SwerveModuleConstants.ClosedLoopOutputType;
import com.ctre.phoenix6.swerve.SwerveModuleConstants.SteerFeedbackType;
import com.ctre.phoenix6.swerve.SwerveModuleConstantsFactory;
import edu.wpi.first.units.measure.*;
import frc.robot.constants.DriveTrainConstants;

// Generated by the Tuner X Swerve Project Generator
// https://v6.docs.ctr-electronics.com/en/stable/docs/tuner/tuner-swerve/index.html
/**
 * For CTRE Chassis, generate this file using the Tuner
 *
 * <p>TODO: you MUST delete the last two lines of this file
 *
 * <p>TODO: you should also change the numbers in {@link DriveTrainConstants}
 */
public class TunerConstants {
    // Both sets of gains need to be tuned to your individual robot.

    // The steer motor uses any SwerveModule.SteerRequestType control request with the
    // output type specified by SwerveModuleConstants.SteerMotorClosedLoopOutput
    public static final Slot0Configs steerGains = new Slot0Configs()
            .withKP(100.0)
            .withKI(0)
            .withKD(1.0)
            .withKS(0.08)
            .withKV(2.66)
            .withKA(0)
            .withStaticFeedforwardSign(StaticFeedforwardSignValue.UseClosedLoopSign);
    // When using closed-loop control, the drive motor uses the control
    // output type specified by SwerveModuleConstants.DriveMotorClosedLoopOutput
    public static final Slot0Configs driveGains =
            new Slot0Configs().withKP(0.08).withKI(0).withKD(0).withKS(0.05).withKV(0.124);

    // The closed-loop output type to use for the steer motors;
    // This affects the PID/FF gains for the steer motors
    public static final ClosedLoopOutputType kSteerClosedLoopOutput = ClosedLoopOutputType.Voltage;
    // The closed-loop output type to use for the drive motors;
    // This affects the PID/FF gains for the drive motors
    public static final ClosedLoopOutputType kDriveClosedLoopOutput = ClosedLoopOutputType.Voltage;

    // The remote sensor feedback type to use for the steer motors;
    // When not Pro-licensed, FusedCANcoder/SyncCANcoder automatically fall back to RemoteCANcoder
    public static final SteerFeedbackType kSteerFeedbackType = SteerFeedbackType.FusedCANcoder;

    // The stator current at which the wheels start to slip;
    // This needs to be tuned to your individual robot
    public static final Current kSlipCurrent = Amps.of(120.0);

    // Initial configs for the drive and steer motors and the CANcoder; these cannot be null.
    // Some configs will be overwritten; check the `with*InitialConfigs()` API documentation.
    public static final TalonFXConfiguration driveInitialConfigs = new TalonFXConfiguration();
    public static final TalonFXConfiguration steerInitialConfigs = new TalonFXConfiguration()
            .withCurrentLimits(new CurrentLimitsConfigs()
                    // Swerve azimuth does not require much torque output, so we can set a relatively low
                    // stator current limit to help avoid brownouts without impacting performance.
                    .withStatorCurrentLimit(Amps.of(60))
                    .withStatorCurrentLimitEnable(true));
    public static final CANcoderConfiguration cancoderInitialConfigs = new CANcoderConfiguration();
    // Configs for the Pigeon 2; leave this null to skip applying Pigeon 2 configs
    public static final Pigeon2Configuration pigeonConfigs = null;

    // CAN bus that the devices are located on;
    // All swerve devices must share the same CAN bus
    public static final CANBus kCANBus = new CANBus("ChassisCanivore", "./logs/example.hoot");

    // Theoretical free speed (m/s) at 12 V applied output;
    // This needs to be tuned to your individual robot
    public static final LinearVelocity kSpeedAt12Volts = MetersPerSecond.of(4.73);

    // Every 1 rotation of the azimuth results in kCoupleRatio drive motor turns;
    // This may need to be tuned to your individual robot
    public static final double kCoupleRatio = 3.5714285714285716;

    public static final double kDriveGearRatio = 5.9;
    public static final double kSteerGearRatio = 12.8;
    public static final Distance kWheelRadius = Inches.of(2);

    public static final boolean kInvertLeftSide = false;
    public static final boolean kInvertRightSide = true;

    public static final int kPigeonId = 0;

    // These are only used for simulation
    public static final double kSteerInertia = 0.01;
    public static final double kDriveInertia = 0.01;
    // Simulated voltage necessary to overcome friction
    public static final Voltage kSteerFrictionVoltage = Volts.of(0.05);
    public static final Voltage kDriveFrictionVoltage = Volts.of(0.05);

    public static final SwerveDrivetrainConstants DrivetrainConstants = new SwerveDrivetrainConstants()
            .withCANBusName(kCANBus.getName())
            .withPigeon2Id(kPigeonId)
            .withPigeon2Configs(pigeonConfigs);

    public static final SwerveModuleConstantsFactory ConstantCreator = new SwerveModuleConstantsFactory()
            .withDriveMotorGearRatio(kDriveGearRatio)
            .withSteerMotorGearRatio(kSteerGearRatio)
            .withCouplingGearRatio(kCoupleRatio)
            .withWheelRadius(kWheelRadius)
            .withSteerMotorGains(steerGains)
            .withDriveMotorGains(driveGains)
            .withSteerMotorClosedLoopOutput(kSteerClosedLoopOutput)
            .withDriveMotorClosedLoopOutput(kDriveClosedLoopOutput)
            .withSlipCurrent(kSlipCurrent)
            .withSpeedAt12Volts(kSpeedAt12Volts)
            .withFeedbackSource(kSteerFeedbackType)
            .withDriveMotorInitialConfigs(driveInitialConfigs)
            .withSteerMotorInitialConfigs(steerInitialConfigs)
            .withCANcoderInitialConfigs(cancoderInitialConfigs)
            .withSteerInertia(kSteerInertia)
            .withDriveInertia(kDriveInertia)
            .withSteerFrictionVoltage(kSteerFrictionVoltage)
            .withDriveFrictionVoltage(kDriveFrictionVoltage);

    // Front Left
    public static final int kFrontLeftDriveMotorId = 1;
    public static final int kFrontLeftSteerMotorId = 2;
    public static final int kFrontLeftEncoderId = 1;
    public static final Angle kFrontLeftEncoderOffset = Rotations.of(0.30322265625);
    public static final boolean kFrontLeftSteerMotorInverted = true;
    public static final boolean kFrontLeftCANcoderInverted = false;

    public static final Distance kFrontLeftXPos = Inches.of(10);
    public static final Distance kFrontLeftYPos = Inches.of(10);

    // Front Right
    public static final int kFrontRightDriveMotorId = 3;
    public static final int kFrontRightSteerMotorId = 4;
    public static final int kFrontRightEncoderId = 2;
    public static final Angle kFrontRightEncoderOffset = Rotations.of(-0.230712890625);
    public static final boolean kFrontRightSteerMotorInverted = true;
    public static final boolean kFrontRightCANcoderInverted = false;

    public static final Distance kFrontRightXPos = Inches.of(10);
    public static final Distance kFrontRightYPos = Inches.of(-10);

    // Back Left
    public static final int kBackLeftDriveMotorId = 5;
    public static final int kBackLeftSteerMotorId = 6;
    public static final int kBackLeftEncoderId = 3;
    public static final Angle kBackLeftEncoderOffset = Rotations.of(-0.221435546875);
    public static final boolean kBackLeftSteerMotorInverted = true;
    public static final boolean kBackLeftCANcoderInverted = false;

    public static final Distance kBackLeftXPos = Inches.of(-10);
    public static final Distance kBackLeftYPos = Inches.of(10);

    // Back Right
    public static final int kBackRightDriveMotorId = 7;
    public static final int kBackRightSteerMotorId = 8;
    public static final int kBackRightEncoderId = 4;
    public static final Angle kBackRightEncoderOffset = Rotations.of(-0.05419921875);
    public static final boolean kBackRightSteerMotorInverted = true;
    public static final boolean kBackRightCANcoderInverted = false;

    public static final Distance kBackRightXPos = Inches.of(-10);
    public static final Distance kBackRightYPos = Inches.of(-10);

    public static final SwerveModuleConstants FrontLeft = ConstantCreator.createModuleConstants(
            kFrontLeftSteerMotorId,
            kFrontLeftDriveMotorId,
            kFrontLeftEncoderId,
            kFrontLeftEncoderOffset,
            kFrontLeftXPos,
            kFrontLeftYPos,
            kInvertLeftSide,
            kFrontLeftSteerMotorInverted,
            kFrontLeftCANcoderInverted);
    public static final SwerveModuleConstants FrontRight = ConstantCreator.createModuleConstants(
            kFrontRightSteerMotorId,
            kFrontRightDriveMotorId,
            kFrontRightEncoderId,
            kFrontRightEncoderOffset,
            kFrontRightXPos,
            kFrontRightYPos,
            kInvertRightSide,
            kFrontRightSteerMotorInverted,
            kFrontRightCANcoderInverted);
    public static final SwerveModuleConstants BackLeft = ConstantCreator.createModuleConstants(
            kBackLeftSteerMotorId,
            kBackLeftDriveMotorId,
            kBackLeftEncoderId,
            kBackLeftEncoderOffset,
            kBackLeftXPos,
            kBackLeftYPos,
            kInvertLeftSide,
            kBackLeftSteerMotorInverted,
            kBackLeftCANcoderInverted);
    public static final SwerveModuleConstants BackRight = ConstantCreator.createModuleConstants(
            kBackRightSteerMotorId,
            kBackRightDriveMotorId,
            kBackRightEncoderId,
            kBackRightEncoderOffset,
            kBackRightXPos,
            kBackRightYPos,
            kInvertRightSide,
            kBackRightSteerMotorInverted,
            kBackRightCANcoderInverted);

    // These must be removed:
    //    public static CommandSwerveDrivetrain createDrivetrain() {
    //        return new CommandSwerveDrivetrain(
    //                DrivetrainConstants, FrontLeft, FrontRight, BackLeft, BackRight
    //        );
    //    }
}
