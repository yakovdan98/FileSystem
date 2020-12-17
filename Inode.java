/*
 * The Inode class is a simplified version of the Linux inode.  Each inode represents a file system
 * object (i.e. a file) and stores the attributes (length of the file, count of file table entries
 * using the file, flag of whether it is being used or not) of the file. Each Inode also has 11 direct
 * pointers pointing to direct blocks, and 1 indirect pointer pointing to an indirect block. Each inode
 * has an iNumber that is used as a unique identifier for the particular inode.
 */
public class Inode {
    private final static int iNodeSize = 32;        // fix to 32 bytes
    private final static int directSize = 11;       // # direct pointers

    public int length;                              // file size in bytes
    public short count;                             // # file-table entries pointing to this
    public short flag;                              // 0 = unused, 1 = used
    public short direct[] = new short[directSize];  // direct pointers
    public short indirect;                          // an indirect pointer

    /*
     * Default constructor for Inode. Initializes all variables to their default values
     * and sets the direct and indirect pointers to -1
     */
    Inode()
    {
        this.length = 0;
        this.count = 0;
        this.flag = 1;
        for(int i = 0; i < directSize; i++)
        {
            direct[i] = -1;
        }
        indirect = -1;
    }

    /*
     * Inode constructor that retrieves Inode from the disk based on the
     * iNumber parameter.
     * @Param iNumber: The unique identifier for the Inode that is being retrieved
     */
    Inode(short iNumber)                                            // retrieving inode from disk
    {
        int blockNumber = 1 + iNumber / 16;                         // location of inode on disk
        byte[] data = new byte[Disk.blockSize];                     // holds data from the inode
        SysLib.rawread(blockNumber, data);                          // read data from block
        int offset = (iNumber % 16) * 32;                           // offset is used to track location in the block

                                                                    // Load variables in order based on where they
        this.length = SysLib.bytes2int(data, offset);               // are located in the Inode
        offset += 4;
        this.count = SysLib.bytes2short(data, offset);
        offset += 2;
        this.flag = SysLib.bytes2short(data, offset);
        offset += 2;


        for(int i = 0; i < directSize; i++)                         // Load 11 direct pointers
        {
            this.direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        this.indirect = SysLib.bytes2short(data, offset);           // Load the indirect pointer
    }

    /*
     * Method that saves an Inode to the disk from memory as the i-th Inode.
     * @param iNumber: The unique identifier for the Inode that is being saved to disk
     * @return blockNumber: The block number that the Inode is saved to
     */
    public int toDisk(short iNumber)
    {
        int blockNumber = (iNumber / 16) + 1;                       // location of inode in memory
        byte[] node = new byte[iNodeSize];                          // holds data from the inode
        int offset = 0;                                             // tracks data in the block

        SysLib.int2bytes(length, node, offset);                     // Reads variables from block and coverts to
        offset += 4;                                                // bytes so that they can be saved to the disk
        SysLib.short2bytes(count, node, offset);
        offset += 2;
        SysLib.short2bytes(flag, node, offset);
        offset += 2;
        for(int i = 0; i < directSize; i++)
        {
            SysLib.short2bytes(direct[i], node, offset);
            offset += 2;
        }
        SysLib.short2bytes(indirect, node, offset);

        offset = (iNumber % 16) * iNodeSize;                           // Reset the offset
        byte[] block = new byte[512];
        SysLib.rawread(blockNumber, block);
        System.arraycopy(node, 0, block, offset, iNodeSize);    // Copy the node data into the block
        SysLib.rawwrite(blockNumber, block);                          // Write over original block with updated block
        return blockNumber;
    }

    /*
     * Method that is used to set the index block for indirect access. This method only sets the index
     * block when all of the direct blocks are currently in used
     * @Param freeBlock: The block where the index block will be located
     * @Return boolean: Returns false if all the direct blocks are not currently being used, or if the indirect
     * block is already in use. Returns true if the indirect block is successfully initialized
     */
    public boolean setIndexBlock(short freeBlock)
    {
        for(int i = 0; i < 11; i++)                                 // Checks whether the direct blocks are in use
        {
            if(this.direct[i] == -1)
            {
                return false;
            }
        }

        if(this.indirect != -1)                                     // Checks whether the indirect block is in use
        {
            return false;
        } else {
            this.indirect = freeBlock;                              // Indirect block is not in use, set to freeBlock
            byte[] block = new byte[512];                           // Block that holds pointers

            for(int i = 0; i < 256; i++)
            {
                SysLib.short2bytes((short)-1, block, i*2);
            }

            SysLib.rawwrite(freeBlock, block);
            return true;
        }
    }

    /*
     * Method that finds the index of a block based upon the seek pointer position. Determines whether a block
     * is available through direct or indirect access.
     * @Param seekPtrPos: The position of the seek pointer
     * @Return short: The target block that is found, -1 indicates that no block was found
     */
    public short findTargetBlock(int seekPtrPos)
    {
        int targetBlock = (seekPtrPos / Disk.blockSize);        // Location of the target block

        if(targetBlock < 11)                                    // If location is less than 11, then block is in direct
        {                                                       // access
            return this.direct[targetBlock];
        }
        else if (this.indirect < 0)                             // Target block is not in direct access, and no indirect
        {                                                       // block exists
            return -1;
        } else {
            byte[] blockData = new byte[Disk.blockSize];        // Block is located in indirect access
            SysLib.rawread(this.indirect, blockData);
            int block = (targetBlock - 11) * 2;
            return SysLib.bytes2short(blockData, block);
        }
    }

    /*
     * Method that is used to set a target block using the given free block.
     * @Param seekPtrPos: The position of the seek pointer
     * @Param freeBlock: The free block to be used
     * @Return Int: Returns 0 for success, or -1, -2, -3 for errors related to registering into
     */
    public int setTargetBlock(int seekPtrPos, short freeBlock)
    {
        int targetBlock = seekPtrPos/Disk.blockSize;            // The target block

        if(targetBlock < 11)                                    // Target block is in direct access
        {
            if(this.direct[targetBlock] >= 0)                   // Target block is already in use
            {
                return -1;
            } else if(targetBlock > 0 && this.direct[targetBlock - 1] == -1)        // The direct block before the target
            {                                                                       // is not in use (should use this
                return -2;                                                          // instead)
            } else {                                            // Direct block is not being used, so use it
                this.direct[targetBlock] = freeBlock;
                return 0;
            }
        } else if(this.indirect < 0)                            // Target block is not in direct blocks, but no indirect
        {                                                       // blocks exist
            return -3;
        } else {
            byte[] blockData = new byte[Disk.blockSize];        // Read the block data from the indirect block
            SysLib.rawread(this.indirect, blockData);
            int block = (targetBlock - 11) * 2;
            if(SysLib.bytes2short(blockData, block) > 0)        // If indirect block is already in use, return -1
            {
                return -1;
            } else {                                            // Use the indirect block
                SysLib.short2bytes(freeBlock, blockData, block);
                SysLib.rawwrite(this.indirect, blockData);
                return 0;
            }
        }
    }

    /*
     * Method that frees the indirect blocks
     * @Return byte[]: The data from the block that has being freed
     */
    public byte[] removeIndexBlock()
    {
        if(this.indirect >= 0)                          // If the block is in used, read the contents into the byte[]
        {                                               // array, set the indirect block to -1 (not in use) and return
            byte[] block = new byte[Disk.blockSize];    // the data
            SysLib.rawread(this.indirect, block);
            this.indirect = -1;
            return block;
        } else {
            return null;
        }
    }
}
