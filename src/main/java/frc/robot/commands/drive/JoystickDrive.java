package frc.robot.commands.drive;

import static frc.robot.constants.JoystickConfigs.*;
import static frc.robot.subsystems.drive.HolonomicDriveSubsystem.isZero;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Robot;
import frc.robot.subsystems.drive.HolonomicDriveSubsystem;
import frc.robot.utils.ChassisHeadingController;
import frc.robot.utils.MapleJoystickDriveInput;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.ironmaple.utils.FieldMirroringUtils;
import org.littletonrobotics.junction.Logger;

public class JoystickDrive extends Command {
    protected final MapleJoystickDriveInput input;
    private final BooleanSupplier useDriverStationCentricSwitch;
    private final Supplier<Integer> povButtonSupplier;
    private final HolonomicDriveSubsystem driveSubsystem;

    protected final Timer previousChassisUsageTimer, previousRotationalInputTimer;
    private ChassisSpeeds currentPilotInputSpeeds;
    private Rotation2d currentRotationMaintenanceSetpoint;

    private double translationalSensitivity, rotationalSensitivity;

    public JoystickDrive(
            MapleJoystickDriveInput input,
            BooleanSupplier useDriverStationCentricSwitch,
            Supplier<Integer> povButtonSupplier,
            HolonomicDriveSubsystem driveSubsystem) {
        super();
        this.input = input;
        this.useDriverStationCentricSwitch = useDriverStationCentricSwitch;
        this.povButtonSupplier = povButtonSupplier;
        this.driveSubsystem = driveSubsystem;
        this.previousChassisUsageTimer = new Timer();
        this.previousChassisUsageTimer.start();
        this.previousRotationalInputTimer = new Timer();
        this.previousRotationalInputTimer.start();

        super.addRequirements(driveSubsystem);
        resetSensitivity();
        ChassisHeadingController.getInstance().setHeadingRequest(new ChassisHeadingController.NullRequest());
    }

    @Override
    public void initialize() {
        this.previousChassisUsageTimer.reset();
        this.previousRotationalInputTimer.reset();
        this.currentPilotInputSpeeds = new ChassisSpeeds();
        this.currentRotationMaintenanceSetpoint = driveSubsystem.getFacing();
    }

    @Override
    public void execute() {
        final ChassisSpeeds newestPilotInputSpeed = input.getJoystickChassisSpeeds(
                driveSubsystem.getChassisMaxLinearVelocityMetersPerSec() * translationalSensitivity,
                driveSubsystem.getChassisMaxAngularVelocity() * rotationalSensitivity);
        currentPilotInputSpeeds = driveSubsystem.constrainAcceleration(
                currentPilotInputSpeeds, newestPilotInputSpeed, Robot.defaultPeriodSecs);

        if (Math.abs(currentPilotInputSpeeds.omegaRadiansPerSecond) > 0.05) previousRotationalInputTimer.reset();

        if (povButtonSupplier.get() != -1)
            this.currentRotationMaintenanceSetpoint = FieldMirroringUtils.getCurrentAllianceDriverStationFacing()
                    .minus(Rotation2d.fromDegrees(povButtonSupplier.get()));

        if (previousRotationalInputTimer.hasElapsed(
                TIME_ACTIVATE_ROTATION_MAINTENANCE_AFTER_NO_ROTATIONAL_INPUT_SECONDS))
            ChassisHeadingController.getInstance()
                    .setHeadingRequest(
                            new ChassisHeadingController.FaceToRotationRequest(currentRotationMaintenanceSetpoint));
        else {
            ChassisHeadingController.getInstance().setHeadingRequest(new ChassisHeadingController.NullRequest());
            currentRotationMaintenanceSetpoint = driveSubsystem.getFacing();
        }

        if (!isZero(currentPilotInputSpeeds)) previousChassisUsageTimer.reset();

        if (previousChassisUsageTimer.hasElapsed(NON_USAGE_TIME_RESET_WHEELS)) {
            driveSubsystem.stop();
            return;
        }

        if (useDriverStationCentricSwitch.getAsBoolean())
            driveSubsystem.runDriverStationCentricChassisSpeeds(currentPilotInputSpeeds, true);
        else driveSubsystem.runRobotCentricChassisSpeeds(currentPilotInputSpeeds);

        Logger.recordOutput("JoystickDrive/previous rotational input time", previousRotationalInputTimer.get());
        Logger.recordOutput(
                "JoystickDrive/rotationMaintainSetPoint",
                new Pose2d(driveSubsystem.getPose().getTranslation(), currentRotationMaintenanceSetpoint));
    }

    public void setSensitivity(double translationalSensitivity, double rotationalSensitivity) {
        this.translationalSensitivity = translationalSensitivity;
        this.rotationalSensitivity = rotationalSensitivity;
    }

    public void resetSensitivity() {
        setSensitivity(DEFAULT_TRANSLATIONAL_SENSITIVITY, DEFAULT_ROTATIONAL_SENSITIVITY);
    }

    @Override
    public void end(boolean interrupted) {
        ChassisHeadingController.getInstance().setHeadingRequest(new ChassisHeadingController.NullRequest());
    }

    public void setRotationMaintenanceSetpoint(Rotation2d setpoint) {
        this.currentRotationMaintenanceSetpoint = setpoint;
    }

    public static Optional<JoystickDrive> instance = Optional.empty();
}
