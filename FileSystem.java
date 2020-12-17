/*
 * This class represents the file system.
 */

public class FileSystem {
    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    public final static int OK = 0;
    public final static int ERROR = -1;

    private SuperBlock superblock;
    private Directory directory;
    private FileTable fileTable;

    /*
     * Constructor for the FileSystem. Receives the number of diskBlocks and creates the file system to be used.
     * Initializes the superblock, the directory, and the file table that will be used by the system for managing
     * files
     * @Param diskBlocks: The number of disk blocks that the file system supports
     */
    public FileSystem(int diskBlocks)
    {
        //create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);

        //create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

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

    /*
     * Method used to format the disk. Erases all data and creates a new directory and fileTable
     * @Param files: Number of files that will be formatted
     * @Return boolean: Whether the format was finished successfully or not
     */
    public boolean format(int files)
    {
        superblock.format(files);
        // Create a new instance of Directory and FileTable
        directory = new Directory(superblock.inodeBlocks);
        fileTable = new FileTable(directory);
        return true;

    }

    /*
     * Method used to open a file.
     * @Param filename: The name of the file
     * @Param mode: The mode that the file should be opened in
     * @Return FileTableEntry: The entry in the fileTable where the file is located, returns null if the open
     * was not successful
     */
    public FileTableEntry open(String filename, String mode)
    {
        //Create and allocate new file table entry using filename and mode
        //from function parameters
        return fileTable.falloc(filename, mode);
    }

    /*
     * Method used to close a file. If the count in ftEnt from the parameter is not 0, then the file is still being
     * used elsewhere, and thus cannot be closed
     * @Param ftEnt: The entry in the FileTable of the file that is being closed
     * @Return boolean: Returns true if the file is successfully closed, otherwise false
     */
    public boolean close(FileTableEntry ftEnt)
    {
        if(ftEnt == null)
        {
            return false;
        }

        synchronized (ftEnt)                // Decrement count by 1, to indicate the file is not in used
        {
            ftEnt.count--;
        }
        if(ftEnt.count == 0)                // Check to make sure that file is not being used elsewhere
        {
            fileTable.ffree(ftEnt);
            return true;
        }
        return false;
    }

    /*
     * Method that returns the size of the file
     * @Param ftEnt: The entry in the FileTable for the file
     * @Return int: The size of the file from the parameter
     */
    public int fsize(FileTableEntry ftEnt)
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

    /*
     * Method that reads a file from memory.  The file must be in "read" or "read/write" mode.
     * @Param ftEnt: The entry in the FileTable for the file
     * @Param buffer: The data of the file being read
     * @Return int: The total size that was read from the file
     */
    public int read(FileTableEntry ftEnt, byte[] buffer)
    {
        int dataRead = 0;
        int totalSize = buffer.length;
        int dataSize = 0;

        synchronized (ftEnt) {                                              // Synchronized to prevent race conditions

            if (ftEnt.mode.equals("r") || ftEnt.mode.equals("w+")) {        // Check the mode

                while (totalSize > 0 && ftEnt.seekPtr < fsize(ftEnt)) {     // Loop to read data
                    int block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr); // Finds the block where the file is located

                    if(block == -1) { break;}                               // Returns -1 if no data is in the block

                    byte blockData[] = new byte[512];
                    SysLib.rawread(block, blockData);                       // Read from disk

                    int blockOffset = ftEnt.seekPtr % 512;

                    // Conditional statements used to ensure that all of the data gets read
                    // Ensures that data is being read until the end of the block, and if the data goes
                    // past the end of the block, then we go through the loop again to find the next block
                    if(512 - blockOffset < fsize(ftEnt) - ftEnt.seekPtr){
                        dataSize = 512 - dataSize;
                    }
                    else{
                        dataSize = fsize(ftEnt) - ftEnt.seekPtr;
                    }
                    if(dataSize > totalSize){
                        dataSize = totalSize;
                    }

                    // Copy the data from the disk to the buffer
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

    /*
     * Method used to write data from the memory to the disk.
     * @Param ftEnt: The entry in the FileTable for the file
     * @Param buffer: The data being written
     * @Return int: The size of the data that was written
     */
    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        // Checks that the parameters are valid, and the ftEnt is not is "read" mode
        if(ftEnt == null || buffer == null || ftEnt.mode.equals("r"))
        {
            return -1;
        }

        int bytes, length, totalWriteSpace, offset, writeLength, nodeWriteSpace;
        byte[] data;
        int nodeLoc;

        synchronized (ftEnt)                                    // Synchronized to prevent race conditions
        {
            bytes = 0;                                          // Number of bytes written
            length = buffer.length;                             // Length of the data to be written

            //Loop that iterates until all the data has been written
            while(bytes < length)
            {
                nodeLoc = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);       //Finds the targetBlock to be written to

                // If the location does not exist, then we must find a new block to write to
                if(nodeLoc == -1)
                {
                    // Get a new free block
                    short freeBlock = (short)this.superblock.getFreeBlock();
                    // Set the block to be written to to the new free block
                    int targetBlock = ftEnt.inode.setTargetBlock(ftEnt.seekPtr, freeBlock);

                    // If the targetBlock is pointing to an indirect block that is not in used, then we must try to
                    // initialize the indirect block
                    if(targetBlock == -3)
                    {
                        short newFreeBlock = (short)this.superblock.getFreeBlock();

                        // Return an error (-1) if the index block cannot be set
                        if(!ftEnt.inode.setIndexBlock(newFreeBlock))
                        {
                            return -1;
                        }

                        // Return an error (-1) if the target block cannot be set
                        if(ftEnt.inode.setTargetBlock(ftEnt.seekPtr, freeBlock) != 0)
                        {
                            return -1;
                        }
                    }

                    // Returns an error(-1) if the target block is already in use
                    if(targetBlock == -2 || targetBlock == -1)
                    {
                        return -1;
                    }

                    // FreeBlock is available, so set the location to the free block
                    nodeLoc = freeBlock;
                }

                // Write the data to the block location until all the bytes have been written, or there is no more
                // space on the block. If the block is filled, then we iterate through the loop again to find a new
                // block to write to
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

                //Reset the ftEnt seek pointer
                if(ftEnt.seekPtr > ftEnt.inode.length)
                {
                    ftEnt.inode.length = ftEnt.seekPtr;
                }
            }
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return bytes;
        }
    }

    /*
     * Method that is used to delete a file from the system
     * @Param filename: The name of the file to be deleted
     * @Return boolean: Returns true if the file was deleted, otherwise false
     */
    public boolean delete(String filename)
    {
        FileTableEntry ftEnt = open(filename, "w");
        return close(ftEnt) && directory.ifree(ftEnt.iNumber);
    }

    /*
     * Method use to set the location of the seek pointer within the FileTableEntry
     * @Param ftEnt: The entry in the FileTable for the file
     * @Param offset: Offset of where the seek pointer should be located
     * @Param whence: The location of where to offset the seek pointer from (start, current position, or end)
     * @Return int: The location of the seek pointer in the ftEnt
     */
    public int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        synchronized (ftEnt)
        {
            if(whence == SEEK_SET)
            {
                // If offset is negative, set seek pointer to start of the file
                if(offset < 0)
                {
                    ftEnt.seekPtr = 0;
                }
                // If offset is larger than the total file size, set the seek pointer to the end of the file
                else if(offset > this.fsize(ftEnt))
                {
                    ftEnt.seekPtr = this.fsize(ftEnt);
                // Otherwise set the seek pointer to the offset location
                } else {
                    ftEnt.seekPtr = offset;
                }
            }
            else if(whence == SEEK_CUR)
            {
                // If the offset is negative and the offset + the seek pointer location is less than 0, set the
                // seek pointer to the start of the file
                if(offset < 0 && (offset + ftEnt.seekPtr < 0))
                {
                    ftEnt.seekPtr = 0;
                // If the offset is greater than the file size, or the seek pointer + the offset is greater than the
                // file size, set the seek pointer to the end of the file
                } else if (offset > this.fsize(ftEnt) || (ftEnt.seekPtr + offset > this.fsize(ftEnt)))
                {
                    ftEnt.seekPtr = this.fsize(ftEnt);
                } else {
                    ftEnt.seekPtr += offset;
                }
            }
            else if(whence == SEEK_END)
            {
                // If the offset + the seek pointer location is less than 0 (past the start of the file) set the seek
                // pointer location to the start of the file
                if(offset < 0 && (offset + ftEnt.seekPtr < 0))
                {
                    ftEnt.seekPtr = 0;
                // If the offset is greater than 0, set the seek pointer location to the end of the file
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
}
