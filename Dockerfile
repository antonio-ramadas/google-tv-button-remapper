FROM eclipse-temurin:17-jdk-jammy@sha256:81336a07f69ac361c7f278183b195e32037ebdb532684996806265a3b47ef516
ARG SDK_TOOLS_VERSION=13114758
ARG SDK_TOOLS_SHA256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/build-tools/36.0.0:${PATH}
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip zip fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-${SDK_TOOLS_VERSION}_latest.zip -o /tmp/tools.zip \
    && echo "${SDK_TOOLS_SHA256}  /tmp/tools.zip" | sha256sum -c - \
    && unzip -q /tmp/tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/tools.zip \
    && yes | sdkmanager --licenses >/dev/null
RUN sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
ARG BUNDLETOOL_VERSION=1.18.3
ARG BUNDLETOOL_SHA256=a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29
RUN curl -fsSL https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar -o /opt/bundletool.jar \
    && echo "${BUNDLETOOL_SHA256}  /opt/bundletool.jar" | sha256sum -c -
WORKDIR /workspace
CMD ["bash", "scripts/build.sh"]
