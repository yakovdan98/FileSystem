public class Inode {
    private final static int iNodeSize = 32;        // fix to 32 bytes
    private final static int directSize = 11;       // # direct pointers

    public int length;                              // file size in bytes
    public short count;                             // # file-table entries pointing to this
    public short usedFlag;                              // 0 = unused, 1 = used
    public short direct[] = new short[directSize];  // direct pointers
    public short indirect;                          // a indirect pointer

    Inode()
    {
        this.length = 0;
        this.count = 0;
        this.usedFlag = 1;
        for(int i = 0; i < directSize; i++)
        {
            direct[i] = -1;
        }
        indirect = -1;
    }

    Inode(short iNumber)                            // retrieving inode from disk
    {
        int blockNumber = 1 + iNumber / 16;         // location of inode on disk
        byte[] data = new byte[Disk.blockSize];     // holds data from the inode
        SysLib.rawread(blockNumber, data);          // read data from block
        int offset = (iNumber % 16) * 32;           // offset is used to track location in the block

        this.length = SysLib.bytes2int(data, offset);
        offset += 4;
        this.count = SysLib.bytes2short(data, offset);
        offset += 2;
        this.usedFlag = SysLib.bytes2short(data, offset);
        offset += 2;
        for(int i = 0; i < directSize; i++)
        {
            this.direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        this.indirect = SysLib.bytes2short(data, offset);
    }

    int toDisk(short iNumber)                       // save to disk as the i-th inode
    {
        int blockNumber = 1 + iNumber / 16;
        byte[] node = new byte[iNodeSize];
        int offset = 0;

        SysLib.int2bytes(length, node, offset);
        offset += 4;
        SysLib.short2bytes(count, node, offset);
        offset += 2;
        SysLib.short2bytes(usedFlag, node, offset);
        offset += 2;
        for(int i = 0; i < directSize; i++)
        {
            SysLib.short2bytes(direct[i], node, offset);
            offset += 2;
        }
        SysLib.short2bytes(indirect, node, offset);

        offset = (iNumber % Disk.blockSize) * iNodeSize;
        byte[] block = new byte[512];
        SysLib.rawread(blockNumber, block);
        System.arraycopy(node, 0, block, offset, iNodeSize);
        SysLib.rawwrite(blockNumber, block);
        return blockNumber;
    }

    public short getIndexBlockNumber()
    {
        return this.indirect;
    }

    public boolean setIndexBlock(short indexBlockNumber)
    {
        for(int i = 0; i < 11; i++)
        {
            if(this.direct[i] == -1)
            {
                return false;
            }
        }

        if(this.indirect != -1)
        {
            return false;
        } else {
            this.indirect = indexBlockNumber;
            byte[] block = new byte[512];

            for(int i = 0; i < 256; i++)
            {
                SysLib.short2bytes((short)-1, block, i*2);
            }

            SysLib.rawwrite(indexBlockNumber, block);
            return true;
        }
    }

    public short findTargetBlock(int offset)
    {
        int targetBlock = (offset / Disk.blockSize);

        if(targetBlock < 11)
        {
            return this.direct[targetBlock];
        }
        else if (this.indirect < 0)
        {
            return -1;
        } else {
            byte[] blockData = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, blockData);
            int block = (targetBlock - 11) * 2;
            return SysLib.bytes2short(blockData, block);
        }
    }

    public int setTargetBlock(int targetBlockNumber, short offset)
    {
        int targetBlock = targetBlockNumber/Disk.blockSize;

        if(targetBlock < 11)
        {
            if(this.direct[targetBlock] >= 0)
            {
                return -1;
            } else if(targetBlock > 0 && this.direct[targetBlock - 1] == -1)
            {
                return -2;
            } else {
                this.direct[targetBlock] = offset;
                return 0;
            }
        } else if(this.indirect < 0)
        {
            return -3;
        } else {
            byte[] blockData = new byte[Disk.blockSize];
            SysLib.rawread(indirect, blockData);
            int block = (targetBlock - 11) * 2;
            if(SysLib.bytes2short(blockData, block) > 0)
            {
                return -1;
            } else {
                SysLib.short2bytes(offset, blockData, block);
                SysLib.rawwrite(this.indirect, blockData);
                return 0;
            }
        }
    }

    public byte[] removeIndexBlock()
    {
        if(this.indirect >= 0)
        {
            byte[] block = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, block);
            this.indirect = -1;
            return block;
        } else {
            return null;
        }
    }
}

