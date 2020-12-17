import java.util.Arrays;

/**
 *  Directory class that holds the different file names that are stored on the disk and the inode number for them
 */

public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private static final int FILE_ENTRY = 64; //max size of each file

    // Directory entries
    private int fsize[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.

    public Directory(int maxInumber) { // directory constructor
        fsize = new int[maxInumber];     // maxInumber = max files
        for (int i = 0; i < maxInumber; i++)
            fsize[i] = 0;                 // all file size initialized to 0
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsize[0] = root.length();        // fsize[0] is the size of "/".
        root.getChars(0, fsize[0], fnames[0], 0); // fnames[0] includes "/"
    }

    /**
     * takes an array of bytes of data and initializes it in the directory
     * @param data data to be turned into directory
     * @return 1 if succesfully completed
     */
    public int bytes2directory(byte data[]) {
        // assumes data[] received directory information from disk
        // initializes the Directory instance with this data[]
        int index = 0;

        for(int i = 0; i < fsize.length; i++){
            fsize[i] = SysLib.bytes2int(data, i); //set the size of file
            index += 4;

            String name = new String(data, index, maxChars * 2); //turn byte data into string
            name.getChars(0, fsize[i], fnames[i], 0);//turn copy string chars into fname array
            index += maxChars * 2;//increases index by 60



        }
        return 1;
    }

    /**
     * takes the directory and turns it into data, an array of bytes
     * @return byte array of data
     */
    public byte[] directory2bytes() {
        // converts and return Directory information into a plain byte array
        // this byte array will be written back to disk
        // note: only meaningfull directory information should be converted
        // into bytes.
        int index = 0;
        byte[] out = new byte[FILE_ENTRY * fsize.length];

        for(int i = 0; i < fsize.length; i++){
            SysLib.int2bytes(fsize[i], out, index);//turn size into byte data
            index += 4;

            String name = new String(fnames[i], 0, maxChars);//create string from char array
            byte[] tmp = name.getBytes();//make byte array out of string
            System.arraycopy(tmp, 0, out, index, tmp.length);//copy byte array into the data array
            index += 60;
        }
        return out;
    }

    /**
     * alocates a new file into the directory
     * @param filename name of new file to be created
     * @return inode number of new file
     */
    public short ialloc(String filename) {
        // filename is the one of a file to be created.
        // allocates a new inode number for this filename
        int inode = 0;
        boolean found = false;

        for (int i = 1; i < fsize.length; i++) {//search for empty spot

            if (filename.length() == fsize[i]) {//see if index filename matches name of file that is being created
                char buff[] = new char[fsize[i]];
                for (int j = 0; j < fsize[i]; j++) {
                    buff[j] = fnames[i][j];
                }
                if(filename.equals(new String(buff))){
                    found = true;//filename already exists
                }
            }

            if (fsize[i] == 0 && inode == 0) {//find fist open slot
                inode = i;
            }

            if(!found && inode != 0){//only create new file if no file with same name was found
                fsize[inode] = filename.length();
                filename.getChars(0, fsize[inode], fnames[inode], 0);
                return (short)inode;
            }
        }
        return -1;
    }

    /**
     * free's up a directory slot
     * @param iNumber inode number to free up
     * @return true if slot was freed
     */
    public boolean ifree(short iNumber) {
        // deallocates this inumber (inode number)
        // the corresponding file will be deleted.
        fsize[iNumber] = 0;
        return true;
    }

    /**
     * finds the inode number for a file in directory
     * @param filename name to search up
     * @return inode number, -1 if file not found
     */
    public short namei(String filename) {
        // returns the inumber corresponding to this filename

        for (int i = 1; i < fsize.length; i++) {//go through directory

            if (filename.length() == fsize[i]) {//if lengths match
                char buff[] = new char[fsize[i]];

                for (int j = 0; j < fsize[i]; j++) {//create buffer char array
                    buff[j] = fnames[i][j];
                }

                if(filename.equals(new String(buff))){//turn buffer array into string and compare
                    return (short)i;//return inode num
                }
            }

        }
        return -1;//if file not found
    }
}
