import java.util.IdentityHashMap;
import java.util.Vector;

/**
 * File Table class is a container for a list of currently open files. This object will store opened files
 * on a vector and remove them once they are closed.
 */


public class FileTable {

    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable( Directory directory ) { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    // major public methods

    /**
     * allocates a a table entry for a file
     * @param filename file to be allocated
     * @param mode mode that the file is opened as
     * @return file table entry that has been created
     */
    public synchronized FileTableEntry falloc( String filename, String mode ) {
        // allocate a new file (structure) table entry for this file name
        // allocate/retrieve and register the corresponding inode using dir



        short iNumber = filename.equals("/") ? 0 : dir.namei(filename); //search for file
        Inode inode;

        while(true){

            if(iNumber > 0){
                inode = new Inode(iNumber);

                if(mode.equals("r")){//read flag
                    inode.flag = 3; //read
                    break;
                }
                else{//write flag
                    inode.flag = 4; //write
                    break;
                }

            }

            if(!mode.equals("r")){//read and write or append
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = 3; //read
                break;

            }
            if(iNumber < 0 && mode.equals("r"))
                return null;

        }
        // increment this inode's count
        inode.count++;

        // immediately write back this inode to the disk
        inode.toDisk(iNumber);

        // return a reference to this file (structure) table entry
        FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
        table.addElement(entry);
        return entry;

    }

    /**
     * free up table of entry
     * @param e entry to be freed
     * @return if entry was succesfully freed
     */
    public synchronized boolean ffree( FileTableEntry e ) {
        // receive a file table entry reference

        Inode inode = new Inode(e.iNumber);
        // free this file table entry.
        // return true if this file table entry found in my table

        if(table.remove(e)) {
            switch (inode.flag){
                case 3:
                    if(inode.count == 1){ inode.flag = 1; } //used
                    break;

                case 4:
                    inode.flag = 1;//used
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + inode.flag);
            }

            //decrease count
            inode.count--;

            // save the corresponding inode to the disk
            inode.toDisk(e.iNumber);
            return true;

        }
        return false;
    }

    /**
     * check if the table is empty
     * @return true table empty, false table has entries
     */
    public synchronized boolean fempty( ) {
        return table.isEmpty( );  // return if table is empty
    }                            // should be called before starting a format
}
