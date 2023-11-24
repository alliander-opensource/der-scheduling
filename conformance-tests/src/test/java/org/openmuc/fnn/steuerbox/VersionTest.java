package org.openmuc.fnn.steuerbox;

import de.fhg.ise.testtool.utils.annotations.label.Description;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class VersionTest {

    public final static String EXPECTED_LIB_IEC_61850_VERSION = "1.5.2";

    @Description("Checks installed library version of libiec61850. Expects v" + EXPECTED_LIB_IEC_61850_VERSION + ".")
    @DisplayName("IEC 61850 Protocol Library version check")
    @Test
    void libIecVersionMatchesExpectedVersion() throws IOException {
        final String libIecLink = "/usr/local/lib/libiec61850.so";
        Path path = Path.of(libIecLink).toRealPath();
        String versionSuffix = path.toString().replaceAll(libIecLink + ".", "");

        Assertions.assertEquals(EXPECTED_LIB_IEC_61850_VERSION, versionSuffix);
    }
}
