
package dev.snowdrop.buildpack;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

public class ContainerLogReader extends ResultCallback.Adapter<Frame> {

    private final Logger logger;

    public ContainerLogReader(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void onNext(Frame object) {
      if (StreamType.STDOUT == object.getStreamType() || StreamType.STDERR == object.getStreamType()) {
        String payload = new String(object.getPayload(), UTF_8);
        if (StreamType.STDOUT == object.getStreamType()) {
          logger.stdout(payload);
        } else if (StreamType.STDERR == object.getStreamType()) {
          logger.stderr(payload);
        }
      }
    }

}
