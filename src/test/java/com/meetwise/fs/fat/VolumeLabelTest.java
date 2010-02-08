
package com.meetwise.fs.fat;

import com.meetwise.fs.BlockDevice;
import com.meetwise.fs.ReadOnlyFileSystemException;
import com.meetwise.fs.util.RamDisk;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public class VolumeLabelTest {

    private BlockDevice dev;
    private FatFileSystem fs;
    private Fat16BootSector bs;
    private AbstractDirectory dirStore;
    
    @Before
    public void setUp() throws IOException {
        this.dev = new RamDisk(8 * 1024 * 1024);

        SuperFloppyFormatter sff = new SuperFloppyFormatter(dev);
        sff.format();
        this.fs = new FatFileSystem(dev, false);
        this.dirStore = fs.getRootDirStore();
        this.bs = (Fat16BootSector) fs.getBootSector();
    }

    @Test
    public void testNothingSet() {
        System.out.println("nothingSet");
        
        assertEquals(Fat16BootSector.DEFAULT_VOLUME_LABEL, fs.getVolumeLabel());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetLabelTooLong() throws IOException {
        System.out.println("setLabel (too long)");

        fs.setVolumeLabel("this is too long for sure");
    }

    @Test(expected=ReadOnlyFileSystemException.class)
    public void testSetLabelReadOnly() throws IOException {
        System.out.println("setLabel (read only)");

        fs.close();
        fs = new FatFileSystem(dev, true);
        fs.setVolumeLabel("the label");
    }
    
    @Test
    public void testSetLabel() throws IOException {
        System.out.println("setLabel");

        final String label = "A Volume";
        fs.setVolumeLabel(label);

        assertEquals(label, fs.getVolumeLabel());
        assertEquals(label, bs.getVolumeLabel());
        assertEquals(label, dirStore.getLabel());

        fs.close();
        
        fs = new FatFileSystem(dev, true);
        dirStore = fs.getRootDirStore();
        bs = (Fat16BootSector) fs.getBootSector();
        
        assertEquals(label, fs.getVolumeLabel());
        assertEquals(label, bs.getVolumeLabel());
        assertEquals(label, dirStore.getLabel());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetLabelInvalidChars() throws IOException {
        System.out.println("setLabel (invalid chars)");

        fs.setVolumeLabel(" invalid");
    }
}
