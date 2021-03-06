package net.bramp.ffmpeg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import net.bramp.ffmpeg.io.ProcessUtils;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

/**
 * Private class to contain common methods for both FFmpeg and FFprobe.
 */
abstract class FFcommon {

  /**
   * Path to the binary (e.g. /usr/bin/ffmpeg)
   */
  final String path;

  /**
   * Function to run FFmpeg. We define it like this so we can swap it out (during testing)
   */
  final ProcessFunction runFunc;

  /**
   * Version string
   */
  String version = null;

  public FFcommon(@Nonnull String path) {
    this(path, new RunProcessFunction());
  }

  protected FFcommon(@Nonnull String path, @Nonnull ProcessFunction runFunction) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(path));
    this.runFunc = checkNotNull(runFunction);
    this.path = path;
  }

  protected BufferedReader wrapInReader(Process p) {
    return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
  }

  protected void throwOnError(Process p) throws IOException {
    try {
      // TODO In java 8 use waitFor(long timeout, TimeUnit unit)
      if (ProcessUtils.waitForWithTimeout(p, 1, TimeUnit.SECONDS) != 0) {
        // TODO Parse the error
        throw new IOException(path + " returned non-zero exit status. Check stdout.");
      }
    } catch (TimeoutException e) {
      throw new IOException("Timed out waiting for " + path + " to finish.");
    }
  }

  public synchronized @Nonnull String version() throws IOException {
    if (this.version == null) {
      Process p = runFunc.run(ImmutableList.of(path, "-version"));
      try {
        BufferedReader r = wrapInReader(p);
        this.version = r.readLine();
        IOUtils.copy(r, NULL_OUTPUT_STREAM, StandardCharsets.UTF_8); // Throw away rest of the
        // output
        throwOnError(p);
      } finally {
        p.destroy();
      }
    }
    return version;
  }

  public String getPath() {
    return path;
  }

  /**
   * Runs ffmpeg with the supplied args. Blocking until finished.
   *
   * @param args
   * @throws IOException
   */
  public void run(List<String> args) throws IOException {
    checkNotNull(args);

    List<String> newArgs = ImmutableList.<String>builder().add(path).addAll(args).build();

    Process p = runFunc.run(newArgs);
    try {
      // TODO Move the IOUtils onto a thread, so that FFmpegProgressListener can be on this thread.

      // Now block reading ffmpeg's stdout. We are effectively throwing away the output.
      IOUtils.copy(wrapInReader(p), System.out, StandardCharsets.UTF_8); // TODO Should I be
      // outputting to stdout?

      throwOnError(p);

    } finally {
      p.destroy();
    }
  }
}
