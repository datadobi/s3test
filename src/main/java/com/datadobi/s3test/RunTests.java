/*
 *
 *  Copyright Datadobi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software

 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.datadobi.s3test;

import com.datadobi.s3test.s3.*;
import com.google.common.collect.ImmutableSet;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RunTests {
    public static void main(String[] args) throws InitializationError, IOException {
        List<Pattern> include = new ArrayList<>();
        List<Pattern> exclude = new ArrayList<>();
        Path configPath = null;
        Path logPath = null;

        int i = 0;
        for (; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                break;
            }

            switch (arg) {
                case "-c", "--config" -> configPath = Path.of(args[++i]);
                case "-e", "--exclude" -> exclude.add(Pattern.compile(args[++i], Pattern.CASE_INSENSITIVE));
                case "-i", "--include" -> include.add(Pattern.compile(args[++i], Pattern.CASE_INSENSITIVE));
                case "-l", "--log" -> logPath = Path.of(args[++i]);
            }
        }

        if (i == args.length) {
            System.err.println("Usage: RunTests [options] S3_URI");
            System.err.println("Options:");
            System.err.println("  -c --config PATH        Load additional configuration from PATH");
            System.err.println("  -e --exclude PATTERN    Exclude tests matching PATTERN");
            System.err.println("  -i --include PATTERN    Include tests matching PATTERN");
            System.err.println("  -l --log PATH           Write test error output and HTTP wire trace to PATH");
            System.exit(1);
        }

        Config config;
        if (configPath != null) {
            config = Config.loadFromToml(configPath);
        } else {
            config = Config.AWS_CONFIG;
        }

        var target = ServiceDefinition.fromURI(args[i]);


        target = target.toBuilder().quirks(config.quirks()).build();

        S3TestBase.DEFAULT_SERVICE = target;

        System.out.println("S3 tests: " + target.host());
        List<String> quirks = target.quirks().stream().map(Quirk::toString).sorted().toList();
        if (!quirks.isEmpty()) {
            System.out.println("Quirks: ");
            for (var quirk : quirks) {
                System.out.println("  " + quirk);
            }
        }
        System.out.println();

        List<Class<?>> classes = new ArrayList<>();

        classes.add(ChecksumTests.class);
        classes.add(ConditionalRequestTests.class);
        classes.add(DeleteObjectsTests.class);
        classes.add(DeleteObjectTests.class);
        classes.add(GetObjectTests.class);
        classes.add(ListBucketsTests.class);
        classes.add(ListObjectsTests.class);
        classes.add(MultiPartUploadTests.class);
        classes.add(ObjectKeyTests.class);
        classes.add(PrefixDelimiterTests.class);
        classes.add(PutObjectTests.class);

        if (logPath != null) {
            S3TestBase.WIRE_LOGGER = new WireLogger(logPath);
        }

        JUnitCore junit = new JUnitCore();
        junit.addListener(new TextListener(logPath));

        for (Class<?> c : classes) {
            BlockJUnit4ClassRunner runner = new BlockJUnit4ClassRunner(c);

            try {
                runner.filter(new Filter() {
                    @Override
                    public boolean shouldRun(Description description) {
                        String methodName = description.getMethodName();
                        if (exclude.stream().anyMatch(e -> e.matcher(methodName).matches())) {
                            System.out.println(methodName + " excluded");
                            return false;
                        }

                        if (!include.isEmpty() && include.stream().noneMatch(i -> i.matcher(methodName).matches())) {
                            System.out.println(methodName + " not included");
                            return false;
                        }

                        return true;
                    }

                    @Override
                    public String describe() {
                        return "Name filter";
                    }
                });
                junit.run(runner);
            } catch (NoTestsRemainException e) {
                System.out.println("Skipping " + runner.getDescription());
            }
        }
    }

    private static class TextListener extends RunListener {
        private final PrintStream stdOut;
        private final @Nullable Path logPath;
        private Failure failure;
        private boolean ignored;

        public TextListener(@Nullable Path logPath) {
            this.logPath = logPath;
            stdOut = System.out;
        }

        @Override
        public void testRunStarted(Description description) {
            stdOut.println("Running " + description);
        }

        public void testStarted(Description description) {
            stdOut.append("  ").append(description.getMethodName());
            failure = null;
            ignored = false;
        }

        public void testFailure(Failure failure) {
            this.failure = failure;
        }

        public void testIgnored(Description description) {
            ignored = true;
            this.failure = null;
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            ignored = true;
            this.failure = failure;
        }

        @Override
        public void testFinished(Description description) throws Exception {
            if (ignored) {
                stdOut.print(" 🙈");
                if (failure != null) {
                    stdOut.print(": " + failure.getException().getMessage());
                }
                stdOut.println();
            } else if (failure != null) {
                stdOut.println(" ❌");
                String shortMessage = getShortFailureMessage(failure);
                stdOut.println("    " + shortMessage);
                if (logPath != null) {
                    Path logDir = Files.createDirectories(
                            this.logPath.resolve(description.getTestClass().getSimpleName())
                                    .resolve(description.getMethodName())
                    );
                    Files.writeString(
                            logDir.resolve("error.log"),
                            failure.getTrace(),
                            StandardCharsets.UTF_8
                    );
                }
            } else {
                stdOut.println(" ✅");
            }
        }

        private static String getShortFailureMessage(Failure failure) {
            Throwable ex = failure.getException();
            String name = ex.getClass().getSimpleName();
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                // keep first line only, truncate if very long
                String firstLine = message.lines().findFirst().orElse("").trim();
                int maxLen = 200;
                if (firstLine.length() > maxLen) {
                    firstLine = firstLine.substring(0, maxLen) + "...";
                }
                return name + ": " + firstLine;
            }
            return name;
        }
    }
}
