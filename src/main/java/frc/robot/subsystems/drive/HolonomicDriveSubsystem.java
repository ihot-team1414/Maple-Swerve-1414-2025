package frc.robot.subsystems.drive;

import static edu.wpi.first.units.Units.Seconds;
import static frc.robot.constants.DriveControlLoops.*;
import static frc.robot.constants.JoystickConfigs.*;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.ModuleConfig;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.pathfinding.Pathfinding;
import com.pathplanner.lib.util.DriveFeedforwards;
import com.pathplanner.lib.util.PathPlannerLogging;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.Subsystem;
import frc.robot.constants.DriveTrainConstants;
import frc.robot.subsystems.vision.apriltags.MapleMultiTagPoseEstimator;
import frc.robot.utils.LocalADStarAK;
import frc.robot.utils.PPRobotConfigPrinter;
import org.ironmaple.utils.FieldMirroringUtils;
import org.ironmaple.utils.mathutils.MapleCommonMath;
import org.littletonrobotics.junction.Logger;

public interface HolonomicDriveSubsystem extends Subsystem {
    /**
     * runs a ChassisSpeeds without doing any pre-processing
     *
     * @param speeds a discrete chassis speed, robot-centric
     */
    void runRobotCentricChassisSpeeds(ChassisSpeeds speeds);

    /**
     * runs a ChassisSpeeds without doing any pre-processing
     *
     * @param speeds a discrete chassis speed, robot-centric
     */
    void runRobotCentricSpeedsWithFeedforwards(ChassisSpeeds speeds, DriveFeedforwards feedforwards);

    /** Returns the current odometry Pose. */
    Pose2d getPose();

    default Pose2d getPoseWithLookAhead(double translationalLookAheadTime, double rotationalLookAheadTime) {
        Pose2d currentPose = getPose();
        ChassisSpeeds speeds = getMeasuredChassisSpeedsFieldRelative();
        Transform2d lookAhead = new Transform2d(
                speeds.vxMetersPerSecond * translationalLookAheadTime,
                speeds.vyMetersPerSecond * translationalLookAheadTime,
                Rotation2d.fromRadians(speeds.omegaRadiansPerSecond * rotationalLookAheadTime));

        return currentPose.plus(lookAhead);
    }

    default Pose2d getPoseWithLookAhead() {
        return getPoseWithLookAhead(TRANSLATIONAL_LOOKAHEAD_TIME, ROTATIONAL_LOOKAHEAD_TIME);
    }

    default Rotation2d getFacing() {
        return getPose().getRotation();
    }

    /** Resets the current odometry Pose to a given Pose */
    void setPose(Pose2d currentPose);

    /**
     * Adds a vision measurement to the pose estimator.
     *
     * @param poseEstimationResult the pose estimation result
     * @param timestamp The timestamp of the vision measurement in seconds.
     */
    default void addVisionMeasurement(
            MapleMultiTagPoseEstimator.RobotPoseEstimationResult poseEstimationResult, double timestamp) {}

    /** @return the measured(actual) velocities of the chassis, robot-relative */
    ChassisSpeeds getMeasuredChassisSpeedsRobotRelative();

    default ChassisSpeeds getMeasuredChassisSpeedsFieldRelative() {
        return ChassisSpeeds.fromRobotRelativeSpeeds(getMeasuredChassisSpeedsRobotRelative(), getFacing());
    }

    double getChassisMaxLinearVelocityMetersPerSec();

    double getChassisMaxAccelerationMetersPerSecSq();

    double getChassisMaxAngularVelocity();

    double getChassisMaxAngularAccelerationRadPerSecSq();

    default PathConstraints getChassisConstrains(double speedMultiplier) {
        return new PathConstraints(
                getChassisMaxLinearVelocityMetersPerSec() * speedMultiplier,
                getChassisMaxAccelerationMetersPerSecSq() * speedMultiplier,
                getChassisMaxAngularVelocity() * speedMultiplier,
                getChassisMaxAngularAccelerationRadPerSecSq() * speedMultiplier);
    }

    /**
     * runs a driverstation-centric ChassisSpeeds
     *
     * @param driverStationCentricSpeeds a continuous chassis speeds, driverstation-centric, normally from a gamepad
     */
    default void runDriverStationCentricChassisSpeeds(ChassisSpeeds driverStationCentricSpeeds, boolean discretize) {
        if (discretize)
            driverStationCentricSpeeds =
                    ChassisSpeeds.discretize(driverStationCentricSpeeds, DISCRETIZE_TIME.in(Seconds));
        final Rotation2d driverStationFacing = FieldMirroringUtils.getCurrentAllianceDriverStationFacing();
        runRobotCentricChassisSpeeds(ChassisSpeeds.fromFieldRelativeSpeeds(
                driverStationCentricSpeeds, getPose().getRotation().minus(driverStationFacing)));
    }

    /**
     * runs a field-centric ChassisSpeeds
     *
     * @param fieldCentricSpeeds a continuous chassis speeds, field-centric, normally from a pid position controller
     */
    default void runFieldCentricChassisSpeeds(ChassisSpeeds fieldCentricSpeeds, boolean discretize) {
        if (discretize) fieldCentricSpeeds = ChassisSpeeds.discretize(fieldCentricSpeeds, DISCRETIZE_TIME.in(Seconds));
        runRobotCentricChassisSpeeds(ChassisSpeeds.fromFieldRelativeSpeeds(
                fieldCentricSpeeds, getPose().getRotation()));
    }

    default void stop() {
        runRobotCentricChassisSpeeds(new ChassisSpeeds());
    }

    default void configHolonomicPathPlannerAutoBuilder() {
        RobotConfig robotConfig = new RobotConfig(
                DriveTrainConstants.ROBOT_MASS,
                DriveTrainConstants.ROBOT_MOI,
                new ModuleConfig(
                        DriveTrainConstants.WHEEL_RADIUS,
                        DriveTrainConstants.CHASSIS_MAX_VELOCITY,
                        DriveTrainConstants.WHEEL_COEFFICIENT_OF_FRICTION,
                        DriveTrainConstants.DRIVE_MOTOR.withReduction(DriveTrainConstants.DRIVE_GEAR_RATIO),
                        DriveTrainConstants.DRIVE_ANTI_SLIP_TORQUE_CURRENT_LIMIT,
                        1),
                DriveTrainConstants.MODULE_TRANSLATIONS);
        System.out.println("Generated pathplanner robot config with drive constants: ");
        PPRobotConfigPrinter.printConfig(robotConfig);
        try {
            robotConfig = RobotConfig.fromGUISettings();
            System.out.println("GUI robot config detected in deploy directory, switching: ");
            PPRobotConfigPrinter.printConfig(robotConfig);
        } catch (Exception e) {
            DriverStation.reportError(e.getMessage(), false);
        }
        AutoBuilder.configure(
                this::getPoseWithLookAhead,
                this::setPose,
                this::getMeasuredChassisSpeedsRobotRelative,
                this::runRobotCentricSpeedsWithFeedforwards,
                new PPHolonomicDriveController(
                        CHASSIS_TRANSLATION_CLOSE_LOOP.toPathPlannerPIDConstants(),
                        CHASSIS_ROTATION_CLOSE_LOOP.toPathPlannerPIDConstants()),
                robotConfig,
                FieldMirroringUtils::isSidePresentedAsRed,
                this);
        Pathfinding.setPathfinder(new LocalADStarAK());
        PathPlannerLogging.setLogActivePathCallback((activePath) -> {
            final Pose2d[] trajectory = activePath.toArray(new Pose2d[0]);
            Logger.recordOutput("Odometry/Trajectory", trajectory);
        });
        PathPlannerLogging.setLogTargetPoseCallback(
                (targetPose) -> Logger.recordOutput("Odometry/TrajectorySetpoint", targetPose));
    }

    static boolean isZero(ChassisSpeeds chassisSpeeds) {
        return Math.abs(chassisSpeeds.omegaRadiansPerSecond) < Math.toRadians(5)
                && Math.abs(chassisSpeeds.vxMetersPerSecond) < 0.05
                && Math.abs(chassisSpeeds.vyMetersPerSecond) < 0.05;
    }

    default ChassisSpeeds constrainAcceleration(
            ChassisSpeeds currentSpeeds, ChassisSpeeds desiredSpeeds, double dtSecs) {
        final double
                MAX_LINEAR_ACCELERATION_METERS_PER_SEC_SQ =
                        getChassisMaxLinearVelocityMetersPerSec() / LINEAR_ACCELERATION_SMOOTH_OUT_SECONDS,
                MAX_ANGULAR_ACCELERATION_RAD_PER_SEC_SQ =
                        getChassisMaxAngularVelocity() / ANGULAR_ACCELERATION_SMOOTH_OUT_SECONDS;

        Translation2d
                currentLinearVelocityMetersPerSec =
                        new Translation2d(currentSpeeds.vxMetersPerSecond, currentSpeeds.vyMetersPerSecond),
                desiredLinearVelocityMetersPerSec =
                        new Translation2d(desiredSpeeds.vxMetersPerSecond, desiredSpeeds.vyMetersPerSecond),
                linearVelocityDifference = desiredLinearVelocityMetersPerSec.minus(currentLinearVelocityMetersPerSec);

        final double maxLinearVelocityChangeIn1Period = MAX_LINEAR_ACCELERATION_METERS_PER_SEC_SQ * dtSecs;
        final boolean desiredLinearVelocityReachableWithin1Period =
                linearVelocityDifference.getNorm() <= maxLinearVelocityChangeIn1Period;
        final Translation2d
                linearVelocityChangeVector =
                        new Translation2d(
                                maxLinearVelocityChangeIn1Period, MapleCommonMath.getAngle(linearVelocityDifference)),
                newLinearVelocity =
                        desiredLinearVelocityReachableWithin1Period
                                ? desiredLinearVelocityMetersPerSec
                                : currentLinearVelocityMetersPerSec.plus(linearVelocityChangeVector);

        final double
                angularVelocityDifference = desiredSpeeds.omegaRadiansPerSecond - currentSpeeds.omegaRadiansPerSecond,
                maxAngularVelocityChangeIn1Period = MAX_ANGULAR_ACCELERATION_RAD_PER_SEC_SQ * dtSecs,
                angularVelocityChange = Math.copySign(maxAngularVelocityChangeIn1Period, angularVelocityDifference);
        final boolean desiredAngularVelocityReachableWithin1Period =
                Math.abs(angularVelocityDifference) <= maxAngularVelocityChangeIn1Period;
        final double newAngularVelocity = desiredAngularVelocityReachableWithin1Period
                ? desiredSpeeds.omegaRadiansPerSecond
                : currentSpeeds.omegaRadiansPerSecond + angularVelocityChange;
        return new ChassisSpeeds(newLinearVelocity.getX(), newLinearVelocity.getY(), newAngularVelocity);
    }
}
