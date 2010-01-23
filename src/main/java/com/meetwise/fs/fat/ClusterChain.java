
package com.meetwise.fs.fat;

import com.meetwise.fs.BlockDevice;
import com.meetwise.fs.FileSystemException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A chain of clusters as stored in a {@link Fat}.
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public class ClusterChain {
    protected final Fat fat;
    private final BlockDevice device;
    private final int clusterSize;
    protected final long dataOffset;
    
    private long startCluster;
    private final boolean readOnly;

    public ClusterChain(Fat fat, int clusterSize,
            long dataOffset, long startCluster, boolean readOnly) {
        
        this.fat = fat;
        if (startCluster != 0)
            this.fat.testCluster(startCluster);
        this.device = fat.getDevice();
        this.dataOffset = dataOffset;
        this.startCluster = startCluster;
        this.clusterSize = clusterSize;
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
    
    public int getClusterSize() {
        return clusterSize;
    }
    
    public Fat getFat() {
        return fat;
    }

    public BlockDevice getDevice() {
        return device;
    }

    /**
     * Returns the first cluster of this chain.
     *
     * @return the chain's first cluster, which may be 0 if this chain does
     *      not contain any clusters
     */
    public long getStartCluster() {
        return startCluster;
    }
    
    /**
     * Calculates the device offset (0-based) for the given cluster and offset
     * within the cluster.
     * 
     * @param cluster
     * @param clusterOffset
     * @return long
     * @throws FileSystemException
     */
    private long getDevOffset(long cluster, int clusterOffset)
            throws FileSystemException {
        
        return dataOffset + clusterOffset +
                ((cluster - Fat.FIRST_CLUSTER) * clusterSize);
    }

    /**
     * Returns the size this {@code ClusterChain} occupies on the device.
     *
     * @return the size this chain occupies on the device in bytes
     */
    public long getLengthOnDisk() {
        if (getStartCluster() == 0) return 0;
        
        final long[] chain = getFat().getChain(getStartCluster());
        return ((long) chain.length) * clusterSize;
    }

    /**
     * Sets the length of this {@code ClusterChain} in bytes. Because a
     * {@code ClusterChain} can only contain full clusters, the new size
     * will always be a multiple of the cluster size.
     *
     * @param size the desired number of bytes the can be stored in
     *      this {@code ClusterChain}
     * @return the true number of bytes this {@code ClusterChain} can contain
     * @throws IOException on error setting the new size
     * @see #setChainLength(int) 
     */
    public long setSize(long size) throws IOException {
        final long nrClusters = ((size + clusterSize - 1) / clusterSize);
        if (nrClusters > Integer.MAX_VALUE)
            throw new IOException("too many clusters");

        setChainLength((int) nrClusters);

        return clusterSize * nrClusters;
    }
    
    /**
     * Sets the length of this cluster chain in clusters.
     *
     * @param nrClusters the new number of clusters this chain should contain
     * @throws IOException on error updating the chain length
     * @see #setSize(long) 
     */
    public void setChainLength(int nrClusters) throws IOException {
        
        if (this.startCluster == 0) {
            final long[] chain;

            chain = fat.allocNew(nrClusters);

            this.startCluster = chain[0];
        } else {
            final long[] chain = fat.getChain(startCluster);

            if (nrClusters != chain.length) {
                if (nrClusters > chain.length) {
                    // Grow
                    int count = nrClusters - chain.length;

                    while (count > 0) {
                        fat.allocAppend(getStartCluster());
                        count--;
                    }
                } else {
                    // Shrink
                    fat.setEof(chain[nrClusters - 1]);
                    for (int i = nrClusters; i < chain.length; i++) {
                        fat.setFree(chain[i]);
                    }
                }
            }
        }
    }
    
    public void readData(long offset, ByteBuffer dest)
            throws IOException {

        int len = dest.remaining();
        final long[] chain = getFat().getChain(startCluster);
        final BlockDevice dev = getDevice();

        int chainIdx = (int) (offset / clusterSize);
        if (offset % clusterSize != 0) {
            int clusOfs = (int) (offset % clusterSize);
            int size = Math.min(len,
                    (int) (clusterSize - (offset % clusterSize) - 1));
            dest.limit(dest.position() + size);

            dev.read(getDevOffset(chain[chainIdx], clusOfs), dest);
            
            offset += size;
            len -= size;
            chainIdx++;
        }

        while (len > 0) {
            int size = Math.min(clusterSize, len);
            dest.limit(dest.position() + size);

            dev.read(getDevOffset(chain[chainIdx], 0), dest);

            len -= size;
            chainIdx++;
        }
    }
    
    public void writeData(long offset, ByteBuffer srcBuf)
            throws IOException {
        
        int len = srcBuf.remaining();
        
        final long[] chain = fat.getChain(getStartCluster());

        int chainIdx = (int) (offset / clusterSize);
        if (offset % clusterSize != 0) {
            int clusOfs = (int) (offset % clusterSize);
            int size = Math.min(len,
                    (int) (clusterSize - (offset % clusterSize) - 1));
            srcBuf.limit(srcBuf.position() + size);

            device.write(getDevOffset(chain[chainIdx], clusOfs), srcBuf);
            
            offset += size;
            len -= size;
            chainIdx++;
        }
        
        while (len > 0) {
            int size = Math.min(clusterSize, len);
            srcBuf.limit(srcBuf.position() + size);

            device.write(getDevOffset(chain[chainIdx], 0), srcBuf);

            len -= size;
            chainIdx++;
        }
        
    }
}