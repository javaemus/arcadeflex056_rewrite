package WIP2.common;

/**
 *
 * @author shadow
 */
public class ptr {

        /**
     * Unsigned byte pointer emulation
     */
    public static class UBytePtr {

        public int bsize = 1;
        public char[] memory;
        public int offset;

        public UBytePtr() {
        }

        public UBytePtr(int size) {
            memory = new char[size];
            offset = 0;
        }

        public UBytePtr(char[] m) {
            set(m, 0);
        }

        public UBytePtr(char[] m, int b) {
            set(m, b);
        }

        public UBytePtr(UBytePtr cp, int b) {
            set(cp.memory, cp.offset + b);
        }

        public UBytePtr(UBytePtr cp) {
            set(cp.memory, cp.offset);
        }

        public void set(char[] m, int b) {
            memory = m;
            offset = b;
        }

        public void set(char[] m) {
            memory = m;
            offset = 0;
        }

        public void set(UBytePtr cp, int b) {
            set(cp.memory, cp.offset + b);
        }

        public void inc() {
            offset += bsize;
        }

        public void dec() {
            offset -= bsize;
        }

        public void inc(int count) {
            offset += count * bsize;
        }

        public void dec(int count) {
            offset -= count * bsize;
        }

        public char read() {
            return (char) (memory[offset] & 0xFF);
        }

        public int read16(int index) {
            return ((memory[offset + index + 1] << 8) & 0xFF) | (memory[offset + index] & 0xFF);
        }

        public int read32(int index)//unchecked!
        {
            int myNumber = ((memory[offset + index] & 0xFF) << 0)
                    | ((memory[offset + index + 1] & 0xFF) << 8)
                    | ((memory[offset + index + 2] & 0xFF) << 16)
                    | ((memory[offset + index + 3] & 0xFF) << 24);
            return myNumber;
        }

        public char read(int index) {
            return (char) (memory[offset + index] & 0xFF);
        }

        public char readinc() {
            return (char) ((memory[(this.offset++)]) & 0xFF);
        }

        public char readdec() {
            return (char) ((memory[(this.offset--)]) & 0xFF);
        }

        public void write16(int index, int value) {
            memory[offset + index + 1] = (char) ((value >> 8) & 0xFF);
            memory[offset + index] = (char) (value & 0xFF);
        }

        public void write(int index, int value) {
            memory[offset + index] = (char) (value & 0xFF);
        }

        public void write(int value) {
            memory[offset] = (char) (value & 0xFF);
        }

        public void writeinc(int value) {
            this.memory[(this.offset++)] = (char) (value & 0xFF);
        }

        public void writedec(int value) {
            this.memory[(this.offset--)] = (char) (value & 0xFF);
        }

        public void and(int value) {
            int tempbase = this.offset;
            char[] tempmemory = this.memory;
            tempmemory[tempbase] = (char) ((tempmemory[tempbase] & (char) value) & 0xFF);
        }

        public void or(int value) {
            int tempbase = this.offset;
            char[] tempmemory = this.memory;
            tempmemory[tempbase] = (char) ((tempmemory[tempbase] | (char) value) & 0xFF);
        }

        public void or(int index, int value) {
            int tempbase = this.offset + index;
            char[] tempmemory = this.memory;
            tempmemory[tempbase] = (char) ((tempmemory[tempbase] | (char) value) & 0xFF);
        }

        public void xor(int value) {
            int tempbase = this.offset;
            char[] tempmemory = this.memory;
            tempmemory[tempbase] = (char) ((tempmemory[tempbase] ^ (char) value) & 0xFF);
        }

        public void xor(int index, int value) {
            int tempbase = this.offset + index;
            char[] tempmemory = this.memory;
            tempmemory[tempbase] = (char) ((tempmemory[tempbase] ^ (char) value) & 0xFF);
        }
    }
    /**
     * Byte Pointer emulation
     */
    public static class BytePtr {

        public int bsize = 1;
        public byte[] memory;
        public int offset;

        public BytePtr() {
        }

        public BytePtr(int size) {
            memory = new byte[size];
            offset = 0;
        }

        public BytePtr(byte[] m) {
            set(m, 0);
        }

        public BytePtr(byte[] m, int b) {
            set(m, b);
        }

        public BytePtr(BytePtr cp, int b) {
            set(cp.memory, cp.offset + b);
        }

        public void set(byte[] m) {
            memory = m;
            offset = 0;
        }

        public void set(byte[] m, int offs) {
            memory = m;
            offset = offs;
        }

        public byte read() {
            return (memory[offset]);
        }

        public byte read(int index) {
            return (memory[offset + index]);
        }
    }

    /**
     * Unsigned Short Ptr emulation
     */
    public static class UShortPtr {

        public int bsize = 2;
        public char[] memory;
        public int offset;

        public UShortPtr() {
        }

        public UShortPtr(int size) {
            memory = new char[size];
            offset = 0;
        }

        public UShortPtr(char[] m) {
            set(m, 0);
        }

        public UShortPtr(char[] m, int b) {
            set(m, b);
        }

        public UShortPtr(UShortPtr cp, int b) {
            set(cp.memory, cp.offset + b);
        }

        public UShortPtr(UShortPtr cp) {
            set(cp.memory, cp.offset);
        }

        public UShortPtr(UBytePtr cp) {
            set(cp.memory, cp.offset);
        }

        public UShortPtr(UBytePtr cp, int b) {
            set(cp.memory, cp.offset + b);
        }
        
        public UShortPtr(int[] cp, int b) {
            set(cp, b);
        }

        public void set(char[] m, int b) {
            memory = m;
            offset = b;
        }
        
        public void set(int[] m, int b) {
            //memory = m;
            //System.out.println(m);
            //System.out.println(memory);
            if (m!=null && memory!=null){
                System.arraycopy(m, 0, memory, 0, m.length);
                offset = b;
            }
        }

        public void set(char[] m) {
            memory = m;
            offset = 0;
        }

        public char read(int index) {
            return (char) (((memory[offset + 1 + index * 2] << 8) & 0xFF) | (memory[offset + index * 2] & 0xFF));
        }

        public void write(int index, char value) {
            memory[offset + index * 2] = (char) (value & 0xFF);
            memory[offset + index * 2 + 1] = (char) ((value >> 8) & 0xFF);
        }
        public void write(char value) {
            memory[offset] = (char) (value & 0xFF);
            memory[offset + 1] = (char) ((value >> 8) & 0xFF);
        }
        public void inc() {
            offset += bsize;
        }

        public void dec() {
            offset -= bsize;
        }

        public void inc(int count) {
            offset += count * bsize;
        }

        public void dec(int count) {
            offset -= count * bsize;
        }
    }

    /**
     * ShortPtr emulation
     */
    public static class ShortPtr {

        public int bsize = 2;
        public byte[] memory;
        public int offset;

        public ShortPtr() {
        }

        public ShortPtr(int size) {
            memory = new byte[size * bsize];
            offset = 0;
        }

        public ShortPtr(ShortPtr cp) {
            memory = cp.memory;
            offset = cp.offset;
        }

        public ShortPtr(ShortPtr cp, int b) {
            memory = cp.memory;
            offset = cp.offset + b;
        }

        public ShortPtr(byte[] m) {
            memory = m;
            offset = 0;
        }

        public ShortPtr(short[] m) {
            memory = new byte[m.length * 2];
            for (int i = 0; i < m.length; i++) {
                memory[i * 2] = (byte) (m[i] & 0xff);
                memory[i * 2 + 1] = (byte) ((m[i] >>> 8) & 0xff);
            }
            offset = 0;
        }

        public short read() {
            return (short) ((memory[offset + 1] & 0xFF) << 8 | (memory[offset] & 0xFF));
        }

        public short read(int index) {
            return (short) ((memory[offset + 1 + index * 2] & 0xFF) << 8 | (memory[offset + index * 2] & 0xFF));
        }

        public short readinc() {
            short read = (short) ((memory[offset + 1] & 0xFF) << 8 | (memory[offset] & 0xFF));
            offset += bsize;
            return read;
        }

        public void write(int index, short data) {
            memory[offset + index * 2] = (byte) (data & 0xff);
            memory[offset + index * 2 + 1] = (byte) ((data >>> 8) & 0xff);
        }

        public void write(short data) {
            memory[offset] = (byte) (data & 0xff);
            memory[offset + 1] = (byte) ((data >>> 8) & 0xff);
        }

        public void writeinc(short data) {
            memory[offset] = (byte) (data & 0xff);
            memory[offset + 1] = (byte) ((data >>> 8) & 0xff);
            offset += bsize;
        }

        public void inc(int count) {
            offset += count * bsize;
        }

        public void inc() {
            offset += bsize;
        }

        public void dec(int count) {
            offset -= count * bsize;
        }

        public void dec() {
            offset -= bsize;
        }

        public void write(int i, int i0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
