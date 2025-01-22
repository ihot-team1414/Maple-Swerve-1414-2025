package frc.robot.subsystems.vision.apriltags;

import static frc.robot.constants.LogPaths.APRIL_TAGS_VISION_PATH;
import static frc.robot.constants.VisionConstants.*;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.*;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import frc.robot.utils.CustomMaths.Statistics;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.littletonrobotics.junction.Logger;

public class MapleMultiTagPoseEstimator {
    private OptionalInt tagToFocus;
    public static final boolean LOG_DETAILED_FILTERING_DATA = true;
    // Robot.CURRENT_ROBOT_MODE != RobotMode.REAL;

    private final AprilTagFieldLayout fieldLayout;
    private final VisionResultsFilter filter;
    private final List<PhotonCameraProperties> camerasProperties;

    public MapleMultiTagPoseEstimator(
            AprilTagFieldLayout aprilTagFieldLayout,
            VisionResultsFilter filter,
            List<PhotonCameraProperties> camerasProperties) {
        this.fieldLayout = aprilTagFieldLayout;
        this.filter = filter;
        this.camerasProperties = camerasProperties;
        tagToFocus = OptionalInt.empty();
    }

    public void enableFocusMode(int tagIdToFocusOn) {
        this.tagToFocus = OptionalInt.of(tagIdToFocusOn);
    }

    public void disableFocusMode() {
        this.tagToFocus = OptionalInt.empty();
    }

    final List<Pose3d> robotPose3dObservationsMultiTag = new ArrayList<>(),
            robotPose3dObservationsSingleTag = new ArrayList<>(),
            observedAprilTagsPoses = new ArrayList<>(),
            observedVisionTargetPoseInFieldLayout = new ArrayList<>();

    private void fetchRobotPose3dEstimationsFromCameraInputs(
            AprilTagVisionIO.CameraInputs[] cameraInputs, Pose2d currentOdometryPose) {
        robotPose3dObservationsMultiTag.clear();
        robotPose3dObservationsSingleTag.clear();
        observedAprilTagsPoses.clear();
        observedVisionTargetPoseInFieldLayout.clear();

        if (cameraInputs.length != camerasProperties.size())
            throw new CameraInputsLengthNotMatchException(cameraInputs.length, camerasProperties.size());

        for (int i = 0; i < cameraInputs.length; i++)
            if (cameraInputs[i].newPipeLineResultAvailable)
                fetchSingleCameraInputs(cameraInputs[i], camerasProperties.get(i), currentOdometryPose);
    }

    private void fetchSingleCameraInputs(
            AprilTagVisionIO.CameraInputs cameraInput,
            PhotonCameraProperties cameraProperty,
            Pose2d currentOdometryPose) {

        calculateVisibleTagsPosesForLog(cameraInput, cameraProperty, currentOdometryPose);

        /* add multi-solvepnp result if present */
        Optional<Pose3d> multiSolvePNPPoseEstimation = calculateRobotPose3dFromMultiSolvePNPResult(
                cameraProperty.robotToCamera, cameraInput.bestFieldToCamera);
        multiSolvePNPPoseEstimation.ifPresent(robotPose3dObservationsMultiTag::add);

        for (int i = 0; i < cameraInput.currentTargetsCount; i++)
            if (tagToFocus.isEmpty() || tagToFocus.getAsInt() == cameraInput.fiducialMarksID[i])
                calculateRobotPose3dFromSingleObservation(
                                cameraProperty.robotToCamera,
                                cameraInput.bestCameraToTargets[i],
                                cameraInput.fiducialMarksID[i])
                        .ifPresent(robotPose3dObservationsSingleTag::add);
    }

    private Pose3d calculateObservedAprilTagTargetPose(
            Transform3d bestCameraToTarget, Transform3d robotToCamera, Pose2d currentOdometryPose) {
        return new Pose3d(currentOdometryPose).transformBy(robotToCamera).transformBy(bestCameraToTarget);
    }

    private Optional<Pose3d> calculateRobotPose3dFromSingleObservation(
            Transform3d robotToCamera, Transform3d cameraToTarget, int tagID) {
        return fieldLayout.getTagPose(tagID).map(tagPose -> tagPose.transformBy(cameraToTarget.inverse())
                .transformBy(robotToCamera.inverse()));
    }

    private Optional<Pose3d> calculateRobotPose3dFromMultiSolvePNPResult(
            Transform3d robotToCamera, Optional<Transform3d> bestFieldToCamera) {
        return bestFieldToCamera.map(
                fieldToCamera -> new Pose3d().transformBy(fieldToCamera).transformBy(robotToCamera.inverse()));
    }

    private void calculateVisibleTagsPosesForLog(
            AprilTagVisionIO.CameraInputs cameraInput,
            PhotonCameraProperties cameraProperty,
            Pose2d currentOdometryPose) {
        if (!LOG_DETAILED_FILTERING_DATA) return;
        for (int i = 0; i < cameraInput.fiducialMarksID.length; i++) {
            if (cameraInput.fiducialMarksID[i] == -1) continue;

            fieldLayout
                    .getTagPose(cameraInput.fiducialMarksID[i])
                    .ifPresent(observedVisionTargetPoseInFieldLayout::add);
            observedAprilTagsPoses.add(calculateObservedAprilTagTargetPose(
                    cameraInput.bestCameraToTargets[i], cameraProperty.robotToCamera, currentOdometryPose));
        }
    }

    private final List<Pose3d> validRobotPoseEstimationsMultiTag = new ArrayList<>(),
            validRobotPoseEstimationsSingleTag = new ArrayList<>(),
            invalidRobotPoseEstimations = new ArrayList<>();

    private void applyFilteringToRawRobotPose3dEstimations() {
        validRobotPoseEstimationsMultiTag.clear();
        validRobotPoseEstimationsSingleTag.clear();
        invalidRobotPoseEstimations.clear();
        for (final Pose3d estimation : robotPose3dObservationsMultiTag)
            if (filter.isResultValid(estimation)) validRobotPoseEstimationsMultiTag.add(estimation);
            else invalidRobotPoseEstimations.add(estimation);

        for (final Pose3d estimation : robotPose3dObservationsSingleTag)
            if (filter.isResultValid(estimation)) validRobotPoseEstimationsSingleTag.add(estimation);
            else invalidRobotPoseEstimations.add(estimation);
    }

    /**
     * using the filtering mechanism, find out the best guess of the robot pose and the standard error
     *
     * @param cameraInputs the inputs of the cameras
     * @return (optionally) the best guess of the robot pose and the standard error, if there are valid targets
     */
    public Optional<VisionObservation> estimateRobotPose(
            AprilTagVisionIO.CameraInputs[] cameraInputs, Pose2d currentOdometryPose, double timeStampSeconds) {
        if (cameraInputs.length != camerasProperties.size())
            throw new IllegalStateException("camera inputs length"
                    + cameraInputs.length
                    + " does not match cameras properties length: "
                    + camerasProperties.size());

        fetchRobotPose3dEstimationsFromCameraInputs(cameraInputs, currentOdometryPose);

        applyFilteringToRawRobotPose3dEstimations();

        if (LOG_DETAILED_FILTERING_DATA) logFilteringData();

        return getEstimationResultFromValidObservations(timeStampSeconds);
    }

    private Optional<VisionObservation> getEstimationResultFromValidObservations(double timeStampSeconds) {
        //        boolean singleTagEstimationsMoreThan1 = validRobotPoseEstimationsSingleTag.size() >= 2;
        //        boolean multiTagEstimationPresent = !validRobotPoseEstimationsMultiTag.isEmpty();
        //        boolean focusModeEnabledAndSingleTagResultPresent =
        //                tagToFocus.isPresent() && (!validRobotPoseEstimationsSingleTag.isEmpty());
        //        boolean resultsCountSufficient =
        //                singleTagEstimationsMoreThan1 || multiTagEstimationPresent ||
        // focusModeEnabledAndSingleTagResultPresent;
        boolean resultsCountSufficient =
                validRobotPoseEstimationsSingleTag.size() + validRobotPoseEstimationsMultiTag.size() > 0;

        if (!resultsCountSufficient) return Optional.empty();

        final List<Statistics.Estimation> robotPoseEstimationsXMeters = new ArrayList<>(),
                robotPoseEstimationsYMeters = new ArrayList<>();
        final List<Statistics.RotationEstimation> robotPoseEstimationsTheta = new ArrayList<>();

        for (Pose3d robotPoseEstimationSingleTag : validRobotPoseEstimationsSingleTag) {
            double translationalStandardError = tagToFocus.isPresent()
                    ? TRANSLATIONAL_STANDARD_ERROR_METERS_FOR_FOCUSED_TAG
                    : TRANSLATIONAL_STANDARD_ERROR_METERS_FOR_SINGLE_OBSERVATION;
            double rotationalStandardError = tagToFocus.isPresent()
                    ? ROTATIONAL_STANDARD_ERROR_RADIANS_FOR_FOCUSED_TAG
                    : ROTATIONAL_STANDARD_ERROR_RADIANS_FOR_SINGLE_OBSERVATION;
            robotPoseEstimationsXMeters.add(
                    new Statistics.Estimation(robotPoseEstimationSingleTag.getX(), translationalStandardError));
            robotPoseEstimationsYMeters.add(
                    new Statistics.Estimation(robotPoseEstimationSingleTag.getY(), translationalStandardError));
            robotPoseEstimationsTheta.add(new Statistics.RotationEstimation(
                    robotPoseEstimationSingleTag.getRotation().toRotation2d(), rotationalStandardError));
        }

        for (Pose3d robotPoseEstimationMultiTag : validRobotPoseEstimationsMultiTag) {
            robotPoseEstimationsXMeters.add(new Statistics.Estimation(
                    robotPoseEstimationMultiTag.getX(), TRANSLATIONAL_STANDARD_ERROR_METERS_FOR_MULTITAG));
            robotPoseEstimationsYMeters.add(new Statistics.Estimation(
                    robotPoseEstimationMultiTag.getY(), TRANSLATIONAL_STANDARD_ERROR_METERS_FOR_MULTITAG));
            robotPoseEstimationsTheta.add(new Statistics.RotationEstimation(
                    robotPoseEstimationMultiTag.getRotation().toRotation2d(),
                    ROTATIONAL_STANDARD_ERROR_RADIANS_FOR_MULTITAG));
        }

        final Statistics.Estimation
                robotPoseFinalEstimationXMeters = Statistics.linearFilter(robotPoseEstimationsXMeters),
                robotPoseFinalEstimationYMeters = Statistics.linearFilter(robotPoseEstimationsYMeters);
        final Statistics.RotationEstimation robotPoseFinalEstimationTheta =
                Statistics.rotationFilter(robotPoseEstimationsTheta);

        final Translation2d translationPointEstimate =
                new Translation2d(robotPoseFinalEstimationXMeters.center(), robotPoseFinalEstimationYMeters.center());
        final Rotation2d rotationPointEstimate = robotPoseFinalEstimationTheta.center();

        final double estimationStandardDevsX = robotPoseFinalEstimationXMeters.standardDeviation(),
                estimationStandardDevsY = robotPoseFinalEstimationYMeters.standardDeviation(),
                estimationStandardErrorTheta = robotPoseFinalEstimationTheta.standardDeviationRad();

        Logger.recordOutput(
                "Vision/MeasurementErrors/translationalStandardDevs",
                Math.hypot(estimationStandardDevsX, estimationStandardDevsY));
        Logger.recordOutput(
                "Vision/MeasurementErrors/rotationalStandardDevs", Math.toDegrees(estimationStandardErrorTheta));

        return Optional.of(VisionObservation.create(
                new Pose2d(translationPointEstimate, rotationPointEstimate),
                Math.hypot(estimationStandardDevsX, estimationStandardDevsY),
                estimationStandardErrorTheta,
                timeStampSeconds));
    }

    /** Log the filtering data */
    private void logFilteringData() {
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/CurrentFilterImplementation", filter.getFilterImplementationName());
        if (!LOG_DETAILED_FILTERING_DATA) return;

        /* these are the detailed filtering data, logging them on RobotRIO1.0 is a bad idea, if you want them, replay the log */
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/ValidPoseEstimationsSingleTags",
                validRobotPoseEstimationsSingleTag.toArray(Pose3d[]::new));
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/ValidPoseEstimationsMultiTags",
                validRobotPoseEstimationsMultiTag.toArray(Pose3d[]::new));
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/InvalidPoseEstimations",
                invalidRobotPoseEstimations.toArray(Pose3d[]::new));
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/VisibleFieldTargets",
                observedVisionTargetPoseInFieldLayout.toArray(Pose3d[]::new));
        Logger.recordOutput(
                APRIL_TAGS_VISION_PATH + "Filtering/AprilTagsObservedPositions/",
                observedAprilTagsPoses.toArray(Pose3d[]::new));
    }

    public record VisionObservation(Pose2d visionPose, Matrix<N3, N1> stdDevs, double timestamp) {
        public static VisionObservation create(
                Pose2d visionPose,
                double translationalStandardDeviation,
                double rotationalStandardDeviation,
                double timestamp) {
            return new VisionObservation(
                    visionPose,
                    VecBuilder.fill(
                            translationalStandardDeviation,
                            translationalStandardDeviation,
                            rotationalStandardDeviation),
                    timestamp);
        }
    }

    public static final class CameraInputsLengthNotMatchException extends IllegalStateException {
        public CameraInputsLengthNotMatchException(int cameraInputsLength, int cameraPropertiesLength) {
            super("camera inputs length"
                    + cameraInputsLength
                    + " does not match cameras properties length: "
                    + cameraPropertiesLength);
        }
    }
}
