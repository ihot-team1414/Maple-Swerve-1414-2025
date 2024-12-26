package frc.robot.subsystems.vision.apriltags;

import java.util.List;
import org.photonvision.PhotonCamera;
import org.photonvision.targeting.PhotonPipelineResult;

public class AprilTagVisionIOReal implements AprilTagVisionIO {
    protected final PhotonCamera[] cameras;

    public AprilTagVisionIOReal(List<PhotonCameraProperties> cameraProperties) {
        if (cameraProperties.size() > 16) throw new IllegalArgumentException("max supported camera count is 16");
        cameras = new PhotonCamera[cameraProperties.size()];

        for (int i = 0; i < cameraProperties.size(); i++) cameras[i] = new PhotonCamera(cameraProperties.get(i).name);
    }

    @Override
    public void updateInputs(VisionInputs inputs) {
        if (inputs.camerasAmount != cameras.length)
            throw new IllegalStateException(
                    "inputs camera amount (" + inputs.camerasAmount + ") does not match actual cameras amount");

        for (int i = 0; i < cameras.length; i++) {
            List<PhotonPipelineResult> results = cameras[i].getAllUnreadResults();
            // System.out.println("camera" + i + " results list length: " + results.size());
            if (cameras[i].isConnected() && !results.isEmpty())
                inputs.camerasInputs[i].fromPhotonPipeLine(results.get(results.size() - 1));
            else inputs.camerasInputs[i].clear(cameras[i].isConnected());
        }
    }

    @Override
    public void close() {
        for (PhotonCamera camera : cameras) camera.close();
    }
}
