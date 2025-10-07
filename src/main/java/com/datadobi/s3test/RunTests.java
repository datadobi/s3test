/*
 *
 *  Copyright 2025 Datadobi
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

import com.datadobi.s3test.s3.S3TestBase;
import com.datadobi.s3test.s3.ServiceDefinition;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
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
        ConfigurationBuilder<BuiltConfiguration> configBuilder =
                ConfigurationBuilderFactory.newConfigurationBuilder();
        Configurator.initialize(configBuilder
                .add(configBuilder.newRootLogger(Level.OFF))
                .add(configBuilder.newLogger("org.apache.http.wire")
                        .addAttribute("level", Level.DEBUG))
                .build(false));


        List<Pattern> include = new ArrayList<>();
        List<Pattern> exclude = new ArrayList<>();
        Path logPath = null;

        int i = 0;
        for (; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                break;
            }

            if (arg.equals("-e") || arg.equals("--exclude")) {
                exclude.add(Pattern.compile(args[++i]));
            } else if (arg.equals("-i") || arg.equals("--include")) {
                include.add(Pattern.compile(args[++i]));
            } else if (arg.equals("-l") || arg.equals("--log")) {
                logPath = Path.of(args[++i]);
            }
        }

        if (i == args.length) {
            System.err.println("Usage: RunTests [options] S3_URI");
            System.err.println("Options:");
            System.err.println("  -e --exclude PATTERN    Exclude tests matching PATTERN");
            System.err.println("  -i --include PATTERN    Include tests matching PATTERN");
            System.err.println("  -l --log PATH           Write test error output and HTTP wire trace to PATH");
            System.exit(1);
        }

        var target = ServiceDefinition.fromURI(args[i++]);

        S3TestBase.DEFAULT = target;

        System.out.println("S3 tests: " + target.host());
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
        private final Path logPath;
        private Failure failure;
        private boolean ignored;
        private Configuration previousConfiguration;

        public TextListener(@Nullable Path logPath) {
            this.logPath = logPath;
            stdOut = System.out;
        }

        private @Nullable Path testLogPath(Description description) throws IOException {
            if (logPath == null) {
                return null;
            }

            Path path = logPath.resolve(description.getTestClass().getSimpleName()).resolve(description.getMethodName());
            return Files.createDirectories(path);
        }

        @Override
        public void testRunStarted(Description description) throws Exception {
            stdOut.println("Running " + description);
        }

        public void testStarted(Description description) throws Exception {
            stdOut.append("  " + description.getMethodName());
            failure = null;
            ignored = false;

            Path logPath = testLogPath(description);
            if (logPath != null) {
                ConfigurationBuilder<BuiltConfiguration> configBuilder =
                        ConfigurationBuilderFactory.newConfigurationBuilder();
                Configuration configuration = configBuilder
                        .add(configBuilder
                                .newAppender("wire", "File")
                                .addAttribute("fileName", logPath.resolve("wire.log"))
                                .add(configBuilder.newLayout("PatternLayout").addAttribute("pattern", "%m%n")))
                        .add(configBuilder.newRootLogger(Level.OFF))
                        .add(configBuilder.newLogger("org.apache.http.wire")
                                .addAttribute("level", Level.DEBUG)
                                .add(configBuilder.newAppenderRef("wire")))
                        .build(false);

                previousConfiguration = LoggerContext.getContext().getConfiguration();
                Configurator.reconfigure(configuration);
            }
        }

        public void testFailure(Failure failure) {
            this.failure = failure;
        }

        public void testIgnored(Description description) {
            ignored = true;
        }

        @Override
        public void testFinished(Description description) throws Exception {
            if (ignored) {
                stdOut.println(" 🙈");
            } else if (failure != null) {
                stdOut.println(" ❌");
                Path logPath = testLogPath(description);
                if (logPath != null) {
                    Files.writeString(
                            logPath.resolve("error.log"),
                            failure.getTrace(),
                            StandardCharsets.UTF_8
                    );
                } else {
                    stdOut.println(failure.getTrimmedTrace());
                }
            } else {
                stdOut.println(" ✅");
            }

            if (previousConfiguration != null) {
                Configurator.reconfigure(previousConfiguration);
            }
        }
    }
}
