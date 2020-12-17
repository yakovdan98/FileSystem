/*
 * Andrew Montgomery, Daniel Yakovlev
 * Class that represents the superblock of the file system.  The superblock is the first disk block and is
 * used to hold the number of disk blocks, the number of inodes, and the block number of the head of the free list in
 * the system.
 */

public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;                             //The number of disk blocks
    public int inodeBlocks;                             //The number of inodes
    public int freeList;                                //The block number of the free list's head

    /*
     * Constructor for the SuperBlock
     * @Param diskSize: The number of blocks that can be supported by the system
     */
    public SuperBlock(int diskSize)
    {
        //read the superblock from disk
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        inodeBlocks = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if(totalBlocks == diskSize && inodeBlocks > 0 && freeList >= 2)
        {
            //disk contents are valid
            return;
        } else {
            //need to format disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

    /*
     * This method is used to format all of the blocks. Sets all of the blocks to free
     * @param nodeCount: The total number of Inodes in the file system
     */
    public void format(int nodeCount)
    {
           this.inodeBlocks = nodeCount;
           Inode node = new Inode();
           byte[] block;

           for(int i = 0; i < inodeBlocks; i++)
           {
               node.flag = 0;
               node.toDisk((short) i);
           }

           freeList = (inodeBlocks / 16) + 2;
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

    /*
     *  Method that syncs the superblock data to the first block in the disk
     */
    public void sync()
    {
        byte[] blockData = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, blockData, 0);
        SysLib.int2bytes(inodeBlocks, blockData, 4);
        SysLib.int2bytes(freeList, blockData, 8);
        SysLib.rawwrite(0, blockData);
    }

    /*
     * Method that gets the next free block
     * @Return int: The location of the free block in the freeList
     */
    public int getFreeBlock()
    {
        int freeBlock = this.freeList;                          // Set freeBlock to the head of the freeList
        if(freeBlock != -1)                                     // Check to see if the freeBlock is in use
        {
            byte[] block = new byte[512];
            SysLib.rawread(freeBlock, block);                   // Read data from freeBlock
            this.freeList = SysLib.bytes2int(block, 0);   // Move the head of the freeList to the next block
            SysLib.int2bytes(0, block, 0);
            SysLib.rawwrite(freeBlock, block);
        }

        return freeBlock;
    }

    /*
     * Method that returns a block to the head of the freeList. The block passed in the parameter becomes the next
     * free block.
     * @Param blockNumber: The block number of the block being returned
     * @Return boolean: returns true if the block was returned to the freeList, otherwise returns false
     */
    public boolean returnBlock(int blockNumber)
    {
        if(blockNumber < 0 || blockNumber > totalBlocks)
            return false;

        byte[] block = new byte[Disk.blockSize];
        SysLib.int2bytes(freeList, block, 0);
        SysLib.rawwrite(blockNumber, block);            // Write back the returned block
        this.freeList = blockNumber;                    // Set freeList head to the blockNumber from parameter
        return true;
    }
}
