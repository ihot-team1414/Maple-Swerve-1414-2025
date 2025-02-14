// Original Source:
// https://github.com/Mechanical-Advantage/AdvantageKit/tree/main/example_projects/advanced_swerve_drive/src/main,
// Copyright 2021-2024 FRC 6328

package frc.robot.subsystems.drive.IO;

import static edu.wpi.first.units.Units.RadiansPerSecond;
import static frc.robot.constants.DriveTrainConstants.*;
import static frc.robot.utils.PhoenixUtil.tryUntilOk;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.Pigeon2Configuration;
import com.ctre.phoenix6.hardware.Pigeon2;
import com.ctre.phoenix6.swerve.SwerveDrivetrainConstants;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.AngularVelocity;
import java.util.Objects;
import java.util.Queue;

/** IO implementation for Pigeon2 */
public class GyroIOPigeon2 implements GyroIO {
    private final Pigeon2 pigeon;
    private final StatusSignal<Angle> yaw;
    private final Queue<Angle> yawPositionInput;
    private final StatusSignal<AngularVelocity> yawVelocity;

    private final boolean configurationOK;

    public GyroIOPigeon2(SwerveDrivetrainConstants drivetrainConstants) {
        this(drivetrainConstants.Pigeon2Id, drivetrainConstants.CANBusName, drivetrainConstants.Pigeon2Configs);
    }

    public GyroIOPigeon2(int Pigeon2Id, String CANbusName, Pigeon2Configuration Pigeon2Configs) {
        pigeon = new Pigeon2(Pigeon2Id, CANbusName);
        final Pigeon2Configuration configs = Objects.requireNonNullElseGet(Pigeon2Configs, Pigeon2Configuration::new);
        configurationOK = tryUntilOk(5, () -> pigeon.getConfigurator().apply(configs, 0.2));
        pigeon.getConfigurator().setYaw(0.0);

        yaw = pigeon.getYaw();
        yawVelocity = pigeon.getAngularVelocityZWorld();
        yawPositionInput = OdometryThread.registerSignalSignal(yaw);

        BaseStatusSignal.setUpdateFrequencyForAll(100.0, yawVelocity);
        BaseStatusSignal.setUpdateFrequencyForAll(ODOMETRY_FREQUENCY, yaw);
        pigeon.optimizeBusUtilization();
    }

    @Override
    public void updateInputs(GyroIOInputs inputs) {
        inputs.configurationFailed = !configurationOK;
        inputs.connected = BaseStatusSignal.refreshAll(yawVelocity).isOK();
        inputs.yawVelocityRadPerSec = yawVelocity.getValue().in(RadiansPerSecond);

        inputs.odometryYawPositions =
                yawPositionInput.stream().map(Rotation2d::new).toArray(Rotation2d[]::new);

        yawPositionInput.clear();

        if (inputs.odometryYawPositions.length > 0)
            inputs.yawPosition = inputs.odometryYawPositions[inputs.odometryYawPositions.length - 1];
    }
}
