# L2B-24. Disposable real Ubuntu24 polkitd/dbus lab image, not a distributable component.
# Packages are acquired ONLY during docker build; runtime has --network none.
FROM ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends dbus polkitd util-linux \
    && rm -rf /var/lib/apt/lists/*
# The review policy is mounted read-only into /mnt, NEVER shipped as a product.
