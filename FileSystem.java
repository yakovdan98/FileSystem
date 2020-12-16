public class FileSystem {
    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    private SuperBlock superblock;
    private Directory directory;
    private FileTable fileTable;

    public FileSystem(int diskBlocks)
    {
        //create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);

        //create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.totalInodes);

        //file table is created, and store directory in the file table
        fileTable = new FileTable(directory);

        //directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if(dirSize > 0)
        {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    public boolean format(int files)
    {
        superblock.format(files);
        // Create a new instance of Directory and FileTable
        directory = new Directory(superblock.inodeBlocks);
        fileTable = new FileTable(directory);
        return true;
    }

    public FileTableEntry open(String filename, String mode)
    {
        //Create and allocate new file table entry using filename and mode
        //from function parameters
        FileTableEntry ftEnt = fileTable.fAlloc(filename, mode);

        //If file table entry is in write mode, deallocate all the blocks
        if(mode.equals("w"))
        {
            if(deallocAllBlocks(ftEnt) == false)
                return null;
        }
        return ftEnt;
    }

    boolean close(FileTableEntry ftEnt)
    {
      if(ftEnt == null)
        {
            return false;
        }

        synchronized (ftEnt)
        {
            ftEnt.count--;
        }
        if(ftEnt.count == 0)
        {
            fileTable.ffree(ftEnt);
            return true;
        }
        return false;
    }

    int fsize(FileTableEntry ftEnt)
    {
       if(ftEnt == null)
        {
            return -1;
        }
        if(ftEnt.inode == null)
        {
            return -1;
        }
        return ftEnt.inode.length;
    }

    int read(FileTableEntry ftEnt, byte[] buffer)
    {
       int dataRead = 0;
        int totalSize = buffer.length;
        int dataSize = 0;

        synchronized (ftEnt) {

            if (ftEnt.mode.equals("r") || ftEnt.mode.equals("w+")) {
                int readSize = buffer.length;

                while (totalSize > 0 && ftEnt.seekPtr < fsize(ftEnt)) {
                    int block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);

                    if(block == -1) { break;}

                    byte blockData[] = new byte[512];
                    SysLib.rawread(block, blockData);

                    int blockOffset = ftEnt.seekPtr % 512;


                    if(512 - blockOffset < fsize(ftEnt) - ftEnt.seekPtr){
                        dataSize = 512 - dataSize;
                    }
                    else{
                        dataSize = fsize(ftEnt) - ftEnt.seekPtr;
                    }
                    if(dataSize > totalSize){
                        dataSize = totalSize;
                    }

                    System.arraycopy(blockData, blockOffset, buffer, dataRead, dataSize);
                    ftEnt.seekPtr += dataSize;
                    totalSize -= dataSize;
                    dataRead += dataSize;


                }
                return dataRead;
            }

            return -1;
        }
    }

    int write(FileTableEntry ftEnt, byte[] buffer)
    {
        if(ftEnt == null || buffer == null || ftEnt.mode.equals("r"))
        {
            return -1;
        }

        int bytes, length, totalWriteSpace, offset, writeLength, nodeWriteSpace;
        byte[] data;
        int nodeLoc;

        synchronized (ftEnt)
        {
            bytes = 0;
            length = buffer.length;

            while(bytes < length)
            {
                nodeLoc = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if(nodeLoc == -1)
                {
                    short freeBlock = (short)this.superblock.getFreeBlock();
                    int targetBlock = ftEnt.inode.setTargetBlock(ftEnt.seekPtr, freeBlock);

                    if(targetBlock == -3)
                    {
                        short newFreeBlock = (short)this.superblock.getFreeBlock();

                        if(!ftEnt.inode.setIndexBlock(newFreeBlock))
                        {
                            return -1;
                        }

                        if(ftEnt.inode.setTargetBlock(ftEnt.seekPtr, freeBlock) != 0)
                        {
                            return -1;
                        }
                    }

                    if(targetBlock == -2 || targetBlock == -1)
                    {
                        return -1;
                    }

                    nodeLoc = freeBlock;
                }

                data = new byte[Disk.blockSize];
                SysLib.rawread(nodeLoc, data);
                offset = ftEnt.seekPtr % Disk.blockSize;
                nodeWriteSpace = Disk.blockSize - offset;
                totalWriteSpace = length - bytes;
                if(nodeWriteSpace > totalWriteSpace)
                {
                    writeLength = totalWriteSpace;
                } else {
                    writeLength = nodeWriteSpace;
                }
                SysLib.rawread(nodeLoc, data);
                System.arraycopy(buffer, bytes, data, offset, writeLength);
                SysLib.rawwrite(nodeLoc, data);
                bytes += writeLength;
                ftEnt.seekPtr += writeLength;
                if(ftEnt.seekPtr > ftEnt.inode.length)
                {
                    ftEnt.inode.length = ftEnt.seekPtr;
                }
            }
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return bytes;
        }
    }


    boolean delete(String filename)
    {
        FileTableEntry ftEnt = open(filename, "w");
        return close(ftEnt) && directory.ifree(ftEnt.iNumber);
    }

    int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        synchronized (ftEnt)
        {
            if(whence == SEEK_SET)
            {
                if(offset < 0)
                {
                    ftEnt.seekPtr = 0;
                }
                else if(offset > this.fsize(ftEnt))
                {
                    ftEnt.seekPtr = this.fsize(ftEnt);
                } else {
                    ftEnt.seekPtr = offset;
                }
            }
            else if(whence == SEEK_CUR)
            {
                if(offset < 0 && (offset + ftEnt.seekPtr < 0))
                {
                    ftEnt.seekPtr = 0;
                } else if (offset > this.fsize(ftEnt) || (ftEnt.seekPtr + offset > this.fsize(ftEnt)))
                {
                    ftEnt.seekPtr = this.fsize(ftEnt);
                } else {
                    ftEnt.seekPtr += offset;
                }
            }
            else if(whence == SEEK_END)
            {
                if(offset < 0 && (offset + ftEnt.seekPtr < 0))
                {
                    ftEnt.seekPtr = 0;
                } else if (offset > this.fsize(ftEnt) || (this.fsize(ftEnt) + offset > this.fsize(ftEnt)))
                {
                    ftEnt.seekPtr = this.fsize(ftEnt);
                } else {
                    ftEnt.seekPtr = this.fsize(ftEnt) + offset;
                }
            }
        }
        return ftEnt.seekPtr;
    }

    private boolean deallocAllBlocks(FileTableEntry ftEnt)
    {
        if(ftEnt == null)
            return false;
        Inode node = ftEnt.inode;
        if(node == null || node.count > 1)
            return false;

        byte[] blockData = ftEnt.inode.removeIndexBlock();
        if(blockData != null)
        {
            short indirectNode;
            while((indirectNode = SysLib.bytes2short(blockData, 0)) != -1)
            {
                this.superblock.returnBlock(indirectNode);
            }
        }

        for(int i = 0; i < 11; i++)
        {
            short directNode = ftEnt.inode.direct[i];
            if(directNode != -1)
            {
                superblock.returnBlock(directNode);
            }
        }

        ftEnt.inode.toDisk(ftEnt.iNumber);
        return true;
    }
}
