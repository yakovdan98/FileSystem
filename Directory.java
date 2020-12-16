/*
 * This class represents the Directory of the file system. This implementation is a single-level design and consists
 * of the "/" root directory.  This is the only directory for the file system.
 */

public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private static final int FILE_ENTRY = 64;

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

    public int bytes2directory(byte data[]) {
        // assumes data[] received directory information from disk
        // initializes the Directory instance with this data[]
        int index = 0;

        for(int i = 0; i < fsize.length; i++){
            fsize[i] = SysLib.bytes2int(data, i);
            index += 4;

            String name = new String(data, index, maxChars * 2);
            name.getChars(0, fsize[i], fnames[i], 0);
            index += maxChars * 2;



        }
        return 1;
    }

    public byte[] directory2bytes() {
        // converts and return Directory information into a plain byte array
        // this byte array will be written back to disk
        // note: only meaningfull directory information should be converted
        // into bytes.
        int index = 0;
        byte[] out = new byte[FILE_ENTRY * fsize.length];

        for(int i = 0; i < fsize.length; i++){
            SysLib.int2bytes(fsize[i], out, index);
            index += 4;

            String name = new String(fnames[i], 0, maxChars);
            byte[] tmp = name.getBytes();
            System.arraycopy(tmp, 0, out, index, tmp.length);
            index += 60;
        }
        return out;
    }

    public short ialloc(String filename) {
        // filename is the one of a file to be created.
        // allocates a new inode number for this filename
        int inode = 0;
        boolean found = false;

        for (int i = 1; i < fsize.length; i++) {

            if (filename.length() == fsize[i]) {
                char buff[] = new char[fsize[i]];
                for (int j = 0; j < fsize[i]; j++) {
                    buff[j] = fnames[i][j];
                }
                if(filename.equals(new String(buff))){
                    found = true;
                }
            }

            if (fsize[i] == 0 && inode == 0) {
                inode = i;
            }

            if(!found && inode != 0){
                fsize[inode] = filename.length();
                filename.getChars(0, fsize[inode], fnames[inode], 0);
                return (short)inode;
            }
        }
        return -1;
    }

    public boolean ifree(short iNumber) {
        // deallocates this inumber (inode number)
        // the corresponding file will be deleted.
        fsize[iNumber] = 0;
        for(int i = 0; i < maxChars; i++)
        {
            fnames[iNumber][i] = '0';
        }

        return true;
    }

    public short namei(String filename) {
        // returns the inumber corresponding to this filename

        for (int i = 1; i < fsize.length; i++) {

            if (filename.length() == fsize[i]) {
                char buff[] = new char[fsize[i]];

                for (int j = 0; j < fsize[i]; j++) {
                    buff[j] = fnames[i][j];
                }

                if(filename.equals(new String(buff))){
                    return (short)i;
                }
            }

        }
        return -1;
    }
}
