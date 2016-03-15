package BIDMach.allreduce

import BIDMat.{Mat,SBMat,CMat,DMat,FMat,IMat,HMat,GDMat,GLMat,GMat,GIMat,GSDMat,GSMat,LMat,SMat,SDMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._
import edu.berkeley.bid.comm._
import scala.collection.parallel._
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


class Command(val ctype:Int, val clen:Int, val bytes:Array[Byte]) {
  val magic = Command.magic;
  val byteData = ByteBuffer.wrap(bytes);
  val intData = byteData.asIntBuffer;
  val floatData = byteData.asFloatBuffer;
  val longData = byteData.asLongBuffer;
  var imach = 0;
  
  def encode() = {}
  def decode() = {}
  
  def this(ctype:Int, clen:Int) = this(ctype, clen, new Array[Byte](4*clen));
  
  override def toString():String = {
    "Command %s, length %d bytes" format (Command.names(ctype), clen);
  }
  
}

object Command {
	val magic = 0xa6b38734;
	final val configCtype = 1;
	final val permuteCtype = 2;
	final val allreduceCtype = 3;
	final val permuteAllreduceCtype = 4;
	final val names = Array[String]("", "config", "permute", "allreduce", "permuteAllreduce");
	
	  
  def toAddress(v:Int):String = {
    val p0 = (v >> 24) & 255;
    val p1 = (v >> 16) & 255;
    val p2 = (v >> 8) & 255;
    val p3 = v & 255;
    "%d.%d.%d.%d" format(p0,p1,p2,p3);
   }
}

class ConfigCommand(clen:Int, bytes:Array[Byte]) extends Command(Command.configCtype, clen, bytes) {
  
  var gmods:IMat = null;
  var gridmachines:IMat = null;
  var workerIPs:IMat = null;
  
  def this(clen:Int) = this(clen, new Array[Byte](clen*4));
  
  def setFields(imach0:Int, gmods0:IMat, gridmachines0:IMat, workerIPs0:IMat) {
    imach = imach0;
    gmods = gmods;
    gridmachines = gridmachines0;
    workerIPs = workerIPs0;
  }
  
  override def encode ():Unit = {
  	intData.rewind();
  	intData.put(imach);
  	intData.put(gmods.length);
  	intData.put(gmods.data, 0, gmods.length);
  	intData.put(gridmachines.length);
  	intData.put(gridmachines.data, 0, gridmachines.length);
  	intData.put(workerIPs.length);
  	intData.put(workerIPs.data, 0, workerIPs.length);
  }
  
  override def decode():Unit = {
  	intData.rewind();
    imach = intData.get();
    val lgmods = intData.get();
    gmods = izeros(lgmods,1);
    intData.get(gmods.data, 0, lgmods);
    val lgm = intData.get();
    gridmachines = izeros(lgm, 1);
    intData.get(gridmachines.data, 0, lgm);
    val lwips = intData.get();
    workerIPs = izeros(lwips, 1);
    intData.get(workerIPs.data, 0, lwips);    
  }
  
  override def toString():String = {
    var ostring = new StringBuilder("Command %s, length %d bytes" format (Command.names(ctype), clen));
    ostring.append("\nGroups:\n")
    for (i <- 0 until gmods.length) {
      ostring.append("%d " format gmods(i));
    }
    ostring.append("\nGridmachines:\n");
    for (i <- 0 until math.min(20, gridmachines.length)) {
      ostring.append("%d " format gridmachines(i));
    }
    ostring.append("\nWorkerIPs:\n");
    for (i <- 0 until math.min(20, gridmachines.length)) {
      ostring.append("%s " format Command.toAddress(workerIPs(i)));
    }
    ostring.append("\n")
    ostring.toString;
  }
}

class PermuteCommand(bytes:Array[Byte]) extends Command(Command.permuteCtype, 2, bytes) {
  
  var seed:Long = 0;
  
  def this() = this(new Array[Byte](2*4));
  
  def setFields(seed0:Long) {
    seed = seed0;
  }
  
  override def encode ():Unit = {
  	longData.rewind();
  	longData.put(seed);
  }
  
  override def decode():Unit = {
  	longData.rewind();
    seed = longData.get();    
  }
  
  override def toString():String = {
     "Command %s, length %d bytes, seed %d" format (Command.names(ctype), clen, seed);
  }
}

class AllreduceCommand(bytes:Array[Byte]) extends Command(Command.allreduceCtype, 4, bytes) {
  
  var round:Int = 0;
  var limit:Long = 0;
  
  def this() = this(new Array[Byte](4*4));
  
  def setFields(round0:Int, limit0:Long) {
    round = round0;
    limit = limit0;
  }
  
  override def encode():Unit = {
  	longData.rewind();
  	longData.put(round);
  	longData.put(limit);
  }
  
  override def decode():Unit = {
  	longData.rewind();
  	round = longData.get().toInt;
    limit = longData.get();    
  }
  
  override def toString():String = {
     "Command %s, length %d bytes, round %d limit %d" format (Command.names(ctype), clen, round, limit);
  }
}

class PermuteAllreduceCommand(bytes:Array[Byte]) extends Command(Command.permuteAllreduceCtype, 6, bytes) {
  
  def this() = this(new Array[Byte](6*4));
  
  var seed:Long = 0;
  var round:Int = 0;
  var limit:Long = 0;
  
  def setFields(round0:Int, seed0:Long, limit0:Long) {
    round = round0;
    seed = seed0;
    limit = limit0;
  }
  
  override def encode():Unit = {
  	longData.rewind();
  	longData.put(round);
  	longData.put(seed);
  	longData.put(limit);
  }
  
  override def decode():Unit = {
  	longData.rewind();
  	round = longData.get().toInt;
  	seed = longData.get();
    limit = longData.get();    
  }
  
  override def toString():String = {
     "Command %s, length %d bytes, round %d seed %d limit %d" format (Command.names(ctype), clen, round, seed, limit);
  }
}



