package frc.robot;

// Copyright (c) 2025 FRC 6328
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by an MIT-style
// license that can be found in the LICENSE file at
// the root directory of this project.

import static edu.wpi.first.units.Units.*;
import static frc.robot.constants.DriveTrainConstants.*;
import static frc.robot.constants.VisionConstants.*;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.Nat;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.*;
import edu.wpi.first.math.interpolation.TimeInterpolatableBuffer;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.Alert;
import frc.robot.subsystems.vision.apriltags.MapleMultiTagPoseEstimator;
import frc.robot.utils.MapleTimeUtils;
import java.util.NoSuchElementException;
import java.util.Optional;

public class RobotState {
    private final Alert visionNoResultAlert = new Alert("Vision No Result", Alert.AlertType.kInfo);
    private double previousVisionResultTimeStamp = 0;

    private final TimeInterpolatableBuffer<Pose2d> poseBuffer;
    private final Matrix<N3, N1> primaryEstimatorOdometryStdDevs;
    private final Matrix<N3, N1> visionSensitiveEstimatorOdometryStdDevs;

    // Odometry
    private SwerveModulePosition[] lastWheelPositions = new SwerveModulePosition[] {
        new SwerveModulePosition(), new SwerveModulePosition(), new SwerveModulePosition(), new SwerveModulePosition()
    };
    // Assume gyro starts at zero
    private Rotation2d gyroOffset = new Rotation2d();
    private Pose2d odometryPoseSensorLess = new Pose2d();
    private Pose2d primaryEstimatorPose = new Pose2d();
    private Pose2d visionSensitivePose = new Pose2d();

    private RobotState() {
        this.poseBuffer = TimeInterpolatableBuffer.createBuffer(POSE_BUFFER_DURATION.in(Seconds));

        this.primaryEstimatorOdometryStdDevs = new Matrix<>(Nat.N3(), Nat.N1());
        primaryEstimatorOdometryStdDevs.set(0, 0, PRIMARY_ESTIMATOR_ODOMETRY_TRANSLATIONAL_STANDARD_ERROR_METERS);
        primaryEstimatorOdometryStdDevs.set(1, 0, PRIMARY_ESTIMATOR_ODOMETRY_TRANSLATIONAL_STANDARD_ERROR_METERS);
        primaryEstimatorOdometryStdDevs.set(2, 0, PRIMARY_ESTIMATOR_GYRO_ROTATIONAL_STANDARD_ERROR_RADIANS);

        this.visionSensitiveEstimatorOdometryStdDevs = new Matrix<>(Nat.N3(), Nat.N1());
        visionSensitiveEstimatorOdometryStdDevs.set(
                0, 0, VISION_SENSITIVE_ESTIMATOR_ODOMETRY_TRANSLATIONAL_STANDARD_ERROR_METERS);
        visionSensitiveEstimatorOdometryStdDevs.set(
                1, 0, VISION_SENSITIVE_ESTIMATOR_ODOMETRY_TRANSLATIONAL_STANDARD_ERROR_METERS);
        visionSensitiveEstimatorOdometryStdDevs.set(
                2, 0, VISION_SENSITIVE_ESTIMATOR_GYRO_ROTATIONAL_STANDARD_ERROR_RADIANS);
    }

    public void resetPose(Pose2d pose) {
        // Gyro offset is the rotation that maps the old gyro rotation (estimated - offset) to the new
        // frame of rotation
        gyroOffset = pose.getRotation().minus(primaryEstimatorPose.getRotation().minus(gyroOffset));
        primaryEstimatorPose = visionSensitivePose = odometryPoseSensorLess = pose;
        poseBuffer.clear();
    }

    public void addOdometryObservation(OdometryObservation observation) {
        Twist2d twist = DRIVE_KINEMATICS.toTwist2d(lastWheelPositions, observation.wheelPositions());
        lastWheelPositions = observation.wheelPositions();
        Pose2d lastOdometryPose = odometryPoseSensorLess;
        odometryPoseSensorLess = odometryPoseSensorLess.exp(twist);
        // Use gyro if connected
        observation.gyroAngle.ifPresent(gyroAngle -> {
            // Add offset to measured angle
            Rotation2d angle = gyroAngle.plus(gyroOffset);
            odometryPoseSensorLess = new Pose2d(odometryPoseSensorLess.getTranslation(), angle);
        });
        // Add pose to buffer at timestamp
        poseBuffer.addSample(observation.timestamp(), odometryPoseSensorLess);
        // Calculate diff from last odometry pose and add onto pose estimate
        Twist2d finalTwist = lastOdometryPose.log(odometryPoseSensorLess);
        primaryEstimatorPose = primaryEstimatorPose.exp(finalTwist);
        visionSensitivePose = visionSensitivePose.exp(finalTwist);
    }

    public void addVisionObservation(MapleMultiTagPoseEstimator.VisionObservation observation) {
        // If measurement is old enough to be outside the pose buffer's time-span, skip.
        try {
            if (poseBuffer.getInternalBuffer().lastKey() - POSE_BUFFER_DURATION.in(Seconds) > observation.timestamp())
                return;
        } catch (NoSuchElementException ex) {
            return;
        }

        // Get odometry based pose at timestamp
        var sample = poseBuffer.getSample(observation.timestamp());
        if (sample.isEmpty()) return;

        primaryEstimatorPose = addVisionObservationToEstimator(
                observation, sample.get(), primaryEstimatorPose, primaryEstimatorOdometryStdDevs);
        visionSensitivePose = addVisionObservationToEstimator(
                observation, sample.get(), visionSensitivePose, visionSensitiveEstimatorOdometryStdDevs);

        previousVisionResultTimeStamp = MapleTimeUtils.getLogTimeSeconds();
    }

    private Pose2d addVisionObservationToEstimator(
            MapleMultiTagPoseEstimator.VisionObservation observation,
            Pose2d odometryPoseSample,
            Pose2d estimatorPose,
            Matrix<N3, N1> estimatorOdometryStdDevs) {
        // sample --> odometryPose transform and backwards of that
        var sampleToOdometryTransform = new Transform2d(odometryPoseSample, odometryPoseSensorLess);
        var odometryToSampleTransform = new Transform2d(odometryPoseSensorLess, odometryPoseSample);
        // get old estimate by applying odometryToSample Transform
        Pose2d estimateAtTime = estimatorPose.plus(odometryToSampleTransform);

        // Calculate 3 x 3 vision matrix
        var r = new double[3];
        for (int i = 0; i < 3; ++i)
            r[i] = observation.stdDevs().get(i, 0) * observation.stdDevs().get(i, 0);

        // Solve for closed form Kalman gain for continuous Kalman filter with A = 0
        // and C = I. See wpimath/algorithms.md.
        Matrix<N3, N3> visionK = new Matrix<>(Nat.N3(), Nat.N3());
        for (int row = 0; row < 3; ++row) {
            double stdDev = estimatorOdometryStdDevs.get(row, 0);
            if (stdDev == 0.0) visionK.set(row, row, 0.0);
            else visionK.set(row, row, stdDev / (stdDev + Math.sqrt(stdDev * r[row])));
        }
        // difference between estimate and vision pose
        Transform2d transform = new Transform2d(estimateAtTime, observation.visionPose());
        // scale transform by visionK
        var kTimesTransform = visionK.times(VecBuilder.fill(
                transform.getX(), transform.getY(), transform.getRotation().getRadians()));
        Transform2d scaledTransform = new Transform2d(
                kTimesTransform.get(0, 0),
                kTimesTransform.get(1, 0),
                Rotation2d.fromRadians(kTimesTransform.get(2, 0)));

        // Recalculate current estimate by applying scaled transform to old estimate
        // then replaying odometry data
        return estimateAtTime.plus(scaledTransform).plus(sampleToOdometryTransform);
    }

    public Rotation2d getRotation() {
        return primaryEstimatorPose.getRotation();
    }

    public Pose2d getSensorLessOdometryPose() {
        return this.odometryPoseSensorLess;
    }

    public Pose2d getEstimatorPose() {
        return this.primaryEstimatorPose;
    }

    public Pose2d getVisionPose() {
        return this.visionSensitivePose;
    }

    public void mergeVisionOdometryToPrimaryOdometry() {
        this.primaryEstimatorPose = this.visionSensitivePose;
    }

    public void updateAlerts() {
        double timeNotVisionResultSeconds = MapleTimeUtils.getLogTimeSeconds() - previousVisionResultTimeStamp;
        visionNoResultAlert.set(timeNotVisionResultSeconds > 10);
        if (visionNoResultAlert.get())
            visionNoResultAlert.setText(
                    String.format("No vision pose estimation for %.2f Seconds", timeNotVisionResultSeconds));
    }

    public record OdometryObservation(
            SwerveModulePosition[] wheelPositions, Optional<Rotation2d> gyroAngle, double timestamp) {}

    private static RobotState instance;

    public static RobotState getInstance() {
        if (instance == null) instance = new RobotState();
        return instance;
    }
}
