public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;                             //The number of disk blocks
    public int totalInodes;                             //The number of inodes
    public int freeList;                                //The block number of the free list's head

    public SuperBlock(int diskSize)
    {
        //read the superblock from disk
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if(totalBlocks == diskSize && totalInodes > 0 && freeList >= 2)
        {
            //disk contents are valid
            return;
        } else {
            //need to format disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

    public void format(int nodeCount)
    {
           this.totalInodes = nodeCount;
           Inode node = new Inode();
           byte[] block = null;

           for(int i = 0; i < totalInodes; i++)
           {
               node.flag = 0;
               node.toDisk((short) i);
           }

           freeList = (totalInodes / 16) + 2;
           for(int i = this.freeList; i < this.totalBlocks; i++)
           {
               block = new byte[Disk.blockSize];
               for(int j = 0; j < Disk.blockSize; j++)
               {
                   block[j] = 0;
               }

               SysLib.int2bytes(i + 1, block, 0);
               SysLib.rawwrite(i, block);
           }

           this.sync();
    }

    public void sync()
    {
        byte[] blockData = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, blockData, 0);
        SysLib.int2bytes(totalInodes, blockData, 4);
        SysLib.int2bytes(freeList, blockData, 8);
        SysLib.rawwrite(0, blockData);
    }

    public int getFreeBlock()
    {
        int freeBlock = this.freeList;
        if(freeBlock != -1)
        {
            byte[] block = new byte[512];
            SysLib.rawread(freeBlock, block);
            this.freeList = SysLib.bytes2int(block, 0);
            SysLib.int2bytes(0, block, 0);
            SysLib.rawwrite(freeBlock, block);
        }

        return freeBlock;
    }

    public boolean returnBlock(int blockNumber)
    {
        if(blockNumber < 0 || blockNumber > totalBlocks)
            return false;

        byte[] block = new byte[Disk.blockSize];
        for(int i = 0; i < Disk.blockSize; i++)
        {
            block[i] = 0;
        }
        SysLib.int2bytes(freeList, block, 0);
        SysLib.rawwrite(blockNumber, block);
        this.freeList = blockNumber;
        return true;
    }
}
