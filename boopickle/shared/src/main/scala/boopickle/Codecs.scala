package boopickle

import java.nio.{ByteOrder, ByteBuffer}
import java.nio.charset.{CharacterCodingException, StandardCharsets}

class Decoder(val buf: ByteBuffer) {
  /**
   * Decodes a single byte
   * @return
   */
  @inline def readByte: Byte = {
    buf.get
  }

  /**
   * Decodes a UTF-8 encoded character (1-3 bytes) and produces a single UTF-16 character
   * @return
   */
  def readChar: Char = {
    val b0 = buf.get.toShort & 0xFF
    if (b0 < 0x80)
      b0.toChar
    else if ((b0 & 0xE0) == 0xC0) {
      val b1 = buf.get.toShort & 0xFF
      ((b0 & 0x1F) << 6 | (b1 & 0x3F)).toChar
    } else if ((b0 & 0xF0) == 0xE0) {
      val s0 = buf.getShort
      ((b0 & 0x0F) << 12 | (s0 & 0x3F00) >> 2 | (s0 & 0x003F)).toChar
    } else
      throw new CharacterCodingException
  }

  /**
   * Decodes a 32-bit integer (1-5 bytes)
   * <pre>
   * 0XXX XXXX                            = 0 to 127
   * 1000 XXXX  b0                        = 128 to 4095
   * 1001 XXXX  b0                        = -1 to -4096
   * 1010 XXXX  b0 b1                     = 4096 to 1048575
   * 1011 XXXX  b0 b1                     = -4097 to -1048576
   * 1100 XXXX  b0 b1 b2                  = 1048576 to 268435455
   * 1101 XXXX  b0 b1 b2                  = -1048577 to -268435456
   * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
   * 1111 ????                            = reserved for special codings
   * </pre>
   * @return
   */
  def readInt: Int = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val b0 = b & 0xF | (b << 27 >> 27)
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          b0 << 8 | b1
        case 0xA | 0xB =>
          val b1 = buf.getShort & 0xFFFF
          b0 << 16 | b1
        case 0xC | 0xD =>
          buf.position(buf.position - 1)
          val b1 = buf.getInt & 0x00FFFFFF
          b0 << 24 | b1
        case 0xE if b == 0xE0 =>
          val b1 = buf.getInt
          b1
        case _ =>
          throw new IllegalArgumentException("Unknown integer coding")
      }
    } else {
      b
    }
  }

  def readRawInt:Int = {
    buf.getInt
  }

  /**
   * Decodes a 64-bit integer (1-9 bytes)
   * <pre>
   * 0XXX XXXX                            = 0 to 127
   * 1000 XXXX  b0                        = 128 to 4095
   * 1001 XXXX  b0                        = -1 to -4096
   * 1010 XXXX  b0 b1                     = 4096 to 1048575
   * 1011 XXXX  b0 b1                     = -4097 to -1048576
   * 1100 XXXX  b0 b1 b2                  = 1048575 to 268435455
   * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435456
   * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
   * 1110 0001  b0 b1 b2 b3 b4 b5 b6 b7   = anything larger
   * 1111 ????                            = reserved for special codings
   * </pre>
   * @return
   */
  def readLong: Long = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val b0 = b & 0xF | (b << 27 >> 27)
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          b0 << 8 | b1
        case 0xA | 0xB =>
          val b1 = buf.getShort & 0xFFFF
          b0 << 16 | b1
        case 0xC | 0xD =>
          buf.position(buf.position - 1)
          val b1 = buf.getInt & 0x00FFFFFF
          b0 << 24 | b1
        case 0xE => if (b == 0xE0)
          buf.getInt
        else if (b == 0xE1) // TODO, revert to normal case+if once ScalaJS compiler is fixed #1589
          buf.getLong
        else
          throw new IllegalArgumentException("Unknown long coding")
        case _ =>
          throw new IllegalArgumentException("Unknown long coding")
      }
    } else {
      b
    }
  }

  def readRawLong:Long = {
    buf.getLong
  }

  /**
   * Decodes a 32-bit integer, or returns the first byte if it doesn't contain a valid encoding marker
   * @return
   */
  def readIntCode: Either[Byte, Int] = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val b0 = b & 0xF | (b << 27 >> 27)
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          Right(b0 << 8 | b1)
        case 0xA | 0xB =>
          val b1 = buf.getShort & 0xFFFF
          Right(b0 << 16 | b1)
        case 0xC | 0xD =>
          buf.position(buf.position - 1)
          val b1 = buf.getInt & 0x00FFFFFF
          Right(b0 << 24 | b1)
        case 0xE if b == 0xE0 =>
          val b1 = buf.getInt
          Right(b1)
        case _ =>
          Left(b.toByte)
      }
    } else {
      Right(b)
    }
  }

  /**
   * Decodes a 64-bit long, or returns the first byte if it doesn't contain a valid encoding marker
   * @return
   */
  def readLongCode: Either[Byte, Long] = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val b0 = b & 0xF | (b << 27 >> 27)
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          Right(b0 << 8 | b1)
        case 0xA | 0xB =>
          val b1 = buf.getShort & 0xFFFF
          Right(b0 << 16 | b1)
        case 0xC | 0xD =>
          buf.position(buf.position - 1)
          val b1 = buf.getInt & 0x00FFFFFF
          Right(b0 << 24 | b1)
        case 0xE => if (b == 0xE0)
          Right(buf.getInt)
        else if (b == 0xE1) // TODO, revert to normal case+if once ScalaJS compiler is fixed #1589
          Right(buf.getLong)
        else
          throw new IllegalArgumentException("Unknown long coding")
        case _ =>
          Left(b.toByte)
      }
    } else {
      Right(b)
    }
  }

  /**
   * Decodes a 32-bit float (4 bytes)
   * @return
   */
  @inline def readFloat: Float = {
    buf.getFloat
  }

  /**
   * Decodes a 64-bit double (8 bytes)
   * @return
   */
  @inline def readDouble: Double = {
    buf.getDouble
  }

  /**
   * Decodes a UTF-8 encoded string
   *
   * @return
   */
  def readString: String = {
    // read string length
    val len = readInt
    StringCodec.decodeUTF8(len, buf)
  }

  /**
   * Decodes a UTF-8 encoded string whose length is already known
   * @param len Length of the string (in bytes)
   * @return
   */
  def readString(len:Int): String = {
    StringCodec.decodeUTF8(len, buf)
  }
}

class Encoder {
  private final val initSize = 4032
  private final val maxIncrement = initSize * 16
  private var buf = ByteBuffer.allocateDirect(initSize).order(ByteOrder.BIG_ENDIAN)
  @inline private def utf8 = StandardCharsets.UTF_8

  /**
   * Makes sure the ByteBuffer has enough space for new data. If not, allocates a new ByteBuffer
   * and copies data from the old one.
   *
   * @param size Number of bytes needed for new data
   * @return
   */
  private def alloc(size: Int): ByteBuffer = {
    if (buf.remaining() < size) {
      // resize the buffer
      val increment = size max (buf.limit min maxIncrement)
      val newBuf = ByteBuffer.allocateDirect(buf.limit + increment).order(ByteOrder.BIG_ENDIAN)
      buf.flip()
      buf = newBuf.put(buf)
    }
    buf
  }

  /**
   * Encodes a single byte
   * @param b Byte to encode
   * @return
   */
  @inline def writeByte(b: Byte): Encoder = {
    alloc(1).put(b)
    this
  }

  /**
   * Encodes a single character using UTF-8 encoding
   *
   * @param c Character to encode
   * @return
   */
  def writeChar(c: Char): Encoder = {
    if (c < 0x80) {
      alloc(1).put(c.toByte)
    } else if (c < 0x800) {
      alloc(2).putShort((0xC080 | (c << 2 & 0x1F00) | (c & 0x3F)).toShort)
    } else {
      alloc(3).put((0xE0 | (c >>> 12)).toByte).put((0x80 | (c >>> 6 & 0x3F)).toByte).put((0x80 | (c & 0x3F)).toByte)
    }
    this
  }

  /**
   * Encodes an integer efficiently in 1 to 5 bytes
   * <pre>
   * 0XXX XXXX                            = 0 to 127
   * 1000 XXXX  b0                        = 128 to 4095
   * 1001 XXXX  b0                        = -1 to -4096
   * 1010 XXXX  b0 b1                     = 4096 to 1048575
   * 1011 XXXX  b0 b1                     = -4097 to -1048576
   * 1100 XXXX  b0 b1 b2                  = 1048575 to 268435455
   * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435456
   * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
   * 1111 ????                            = reserved for special codings
   * </pre>
   * @param i Integer to encode
   */
  def writeInt(i: Int): Encoder = {
    // check for a short number
    if (i >= 0) {
      if (i < 128) {
        alloc(1).put(i.toByte)
      } else if (i < 4096) {
        alloc(2).putShort((0x8000 | i).toShort)
      } else if (i < 1048575) {
        alloc(3).putShort((0xA000 | (i >> 8)).toShort).put((i & 0xFF).toByte)
      } else if (i < 268435455) {
        alloc(4).putInt(0xC0000000 | i)
      } else {
        alloc(5).put(0xE0.toByte).putInt(i)
      }
    } else {
      if (i >= -4096) {
        alloc(2).putShort((0x9000 | (i & 0x0FFF)).toShort)
      } else if (i >= -1048576) {
        alloc(3).putShort((0xB000 | ((i >> 8) & 0x0FFF)).toShort).put((i & 0xFF).toByte)
      } else if (i >= -268435456) {
        alloc(4).putInt(0xD0000000 | (i & 0x0FFFFFFF))
      } else {
        alloc(5).put(0xE0.toByte).putInt(i)
      }
    }

    this
  }


  /**
   * Encodes an integer in 32-bits
   * @param i Integer to encode
   * @return
   */
  def writeRawInt(i:Int):Encoder = {
    alloc(4).putInt(i)
    this
  }

  /**
   * Encodes a long efficiently in 1 to 9 bytes
   * <pre>
   * 0XXX XXXX                            = 0 to 127
   * 1000 XXXX  b0                        = 128 to 4095
   * 1001 XXXX  b0                        = -1 to -4096
   * 1010 XXXX  b0 b1                     = 4096 to 1048575
   * 1011 XXXX  b0 b1                     = -4097 to -1048576
   * 1100 XXXX  b0 b1 b2                  = 1048575 to 268435455
   * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435456
   * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
   * 1110 0001  b0 b1 b2 b3 b4 b5 b6 b7   = anything larger
   * 1111 ????                            = reserved for special codings
   * </pre>
   * @param l Long to encode
   */
  def writeLong(l: Long): Encoder = {
    if (l <= Int.MaxValue && l >= Int.MinValue)
      writeInt(l.toInt)
    else {
      alloc(9).put(0xE1.toByte).putLong(l)
    }
    this
  }

  /**
   * Encodes a long in 64-bits
   * @param l Long to encode
   * @return
   */
  def writeRawLong(l:Long):Encoder = {
    alloc(8).putLong(l)
    this
  }
  /**
   * Encodes a string using UTF8
   *
   * @param s String to encode
   * @return
   */
  def writeString(s: String): Encoder = {
    val strBytes = StringCodec.encodeUTF8(s)
    writeInt(strBytes.limit)
    alloc(strBytes.limit).put(strBytes)
    this
  }

  /**
   * Encodes a float as 4 bytes
   *
   * @param f Float to encode
   * @return
   */
  @inline def writeFloat(f: Float): Encoder = {
    alloc(4).putFloat(f)
    this
  }

  /**
   * Encodes a double as 8 bytes
   *
   * @param d Double to encode
   * @return
   */
  @inline def writeDouble(d: Double): Encoder = {
    alloc(8).putDouble(d)
    this
  }

  /**
   * Completes the encoding and returns the ByteBuffer
   * @return
   */
  def encode = {
    buf.flip()
    buf
  }
}