import java.util.Vector;

public class FileTable {

    private Vector table;
    private Directory dir;

    public FileTable(Directory directory)
    {
        table = new Vector();       // instantiate a file (structure) table
        dir = directory;            // receive a reference to the Directory
    }                               // from the file system

    //major public methods
    public synchronized FileTableEntry falloc(String filename, String mode)
    {
        short iNumber;
        Inode iNode;

        while(true)
        {
            //  iNumber = (short) filename.equals("/");

        }
        // allocate a new file (structure) table entry for this file name
        // allocate/retrieve and register the corresponding inode using dir
        // increment this inode's count
        // immediately write back this inode to the disk
        // return a reference to this file (structure) table entry
    }

    public synchronized boolean ffree(FileTableEntry e)
    {
        // receive a file table entry reference
        // save the correspond inode to the disk
        //free this file table entry
        // return true if this file table entry found in my table
    }

    public synchronized boolean fempty()
    {
        return table.isEmpty();     // return if table is empty
    }                               // should be called before starting a format

}

