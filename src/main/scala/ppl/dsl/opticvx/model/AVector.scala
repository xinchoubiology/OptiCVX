package ppl.dsl.opticvx.model

import ppl.dsl.opticvx.common._
import scala.collection.immutable.Seq

object AVector {
  // def input(at: IRPoly, len: IRPoly): AVector = {
  //   if(at.arity != len.arity) throw new IRValidationException()
  //   AVectorCatFor(len, AVectorScaleInput(AVectorOne(at.arity + 1), at.promote + at.next))
  // }
  def const(c: Double, input: InputDesc): AVector = {
    AVectorScaleConstant(AVectorOne(input), c)
  }
}

trait AVector extends HasInput[AVector] {
  val size: IRPoly
  
  // def translate[V](implicit e: AVectorLike[V]): V
  
  // def translateCheck[V <: HasInput[V]](tv: =>V)(implicit e: AVectorLike[V]): V = {
  //   if(e.arity != arity) throw new IRValidationException()
  //   if(e.input != input) throw new IRValidationException()
  //   val v: V = tv
  //   if(v.arity != arity) throw new IRValidationException()
  //   if(v.input != input) throw new IRValidationException()
  //   if(e.size(v) != size) throw new IRValidationException()
  //   v
  // }
  
  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V

  def evalcheck[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W])(tv: V): V = 
  {
    /*
    if(size.eval(params)(runtime.intlikei) != runtime.size(tv)) {
      println(params)
      println(size)
      println(size.eval(params)(runtime.intlikei))
      println(runtime.size(tv))
      println(this)
      println(memory(10))
      throw new IRValidationException()
    }
    */
    tv
  }

  def arityVerify() {
    if(size.arity != arity) throw new IRValidationException()
    if(input.arity != arity) throw new IRValidationException()
    //if(!(size.isNonNegative)) throw new IRValidationException()
  }

  def +(u: AVector) = AVectorSum(this, u)
  def -(u: AVector) = AVectorSum(this, AVectorNeg(u))
  def unary_-() = AVectorNeg(this)
  def ++(u: AVector) = AVectorCat(this, u)
  def *(u: AVector) = AVectorMpy(this, u)
  def /(u: AVector) = AVectorDiv(this, u)
  def apply(at: IRPoly, size: IRPoly) = AVectorSlice(this, at, size)
  def is0: Boolean
  def isPure: Boolean
  def simplify: AVector
}

case class AVectorZero(val input: InputDesc, val size: IRPoly) extends AVector {
  val arity: Int = size.arity
  arityVerify()
  def arityOp(op: ArityOp): AVector = AVectorZero(input.arityOp(op), size.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorZero(op.input, size)
  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.zero(size)
  // }
  def is0: Boolean = true
  def isPure: Boolean = true

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.zero(size.eval(params)(runtime.intlikei))
  }

  def simplify: AVector = this

  override def toString: String = "zero(" + size.toString + ")"
}

case class AVectorOne(val input: InputDesc) extends AVector {
  val arity: Int = input.arity
  val size: IRPoly = IRPoly.const(1, arity)
  arityVerify()
  def arityOp(op: ArityOp): AVector = {
    if(op.xs.length != arity) throw new IRValidationException()
    AVectorOne(input.arityOp(op))
  }
  def inputOp(op: InputOp): AVector = AVectorOne(op.input)
  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.one
  // }
  def is0: Boolean = false
  def isPure: Boolean = true

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.one
  }

  def simplify: AVector = this

  override def toString: String = "one()"
}

case class AVectorSum(val arg1: AVector, val arg2: AVector) extends AVector {
  val arity: Int = arg1.arity
  val input: InputDesc = arg1.input
  val size: IRPoly = arg1.size

  if(arg1.arity != arg2.arity) throw new IRValidationException()
  if(arg1.input != arg2.input) throw new IRValidationException()
  if(arg1.size != arg2.size) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = {
    if(op.xs.length != arity) throw new IRValidationException()
    AVectorSum(arg1.arityOp(op), arg2.arityOp(op))
  }
  def inputOp(op: InputOp): AVector = AVectorSum(arg1.inputOp(op), arg2.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.sum(arg1.translate, arg2.translate)
  // }

  def is0: Boolean = arg1.is0 && arg2.is0
  def isPure: Boolean = arg1.isPure && arg2.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.sum(
      arg1.eval(runtime, params, inputs, memory), 
      arg2.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa1 = arg1.simplify
    val sa2 = arg2.simplify
    if(sa1.is0) {
      sa2
    }
    else if(sa2.is0) {
      sa1
    }
    else {
      AVectorSum(sa1, sa2)
    }
  }

  override def toString: String = "sum(" + arg1.toString + ", " + arg2.toString + ")"
}

case class AVectorNeg(val arg: AVector) extends AVector {
  val arity: Int = arg.arity
  val input: InputDesc = arg.input
  val size: IRPoly = arg.size

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorNeg(arg.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorNeg(arg.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.neg(arg.translate)
  // }


  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.neg(
      arg.eval(runtime, params, inputs, memory))
  }

  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure

  def simplify: AVector = {
    val sa = arg.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorNeg(sa)
    }
  }

  override def toString: String = "neg(" + arg.toString + ")"
}

/*
case class AVectorScaleInput(val arg: AVector, val scale: IRPoly) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = arg.size

  if(arg.arity != scale.arity) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorScaleInput(arg.arityOp(op), scale.arityOp(op))

  def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = e.scaleinput(arg.translate, scale)

  def is0: Boolean = arg.is0
  def isPure: Boolean = false
}
*/

case class AVectorScaleConstant(val arg: AVector, val scale: Double) extends AVector {
  val arity: Int = arg.arity
  val input: InputDesc = arg.input
  val size: IRPoly = arg.size

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorScaleConstant(arg.arityOp(op), scale)
  def inputOp(op: InputOp): AVector = AVectorScaleConstant(arg.inputOp(op), scale)

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.scaleconstant(arg.translate, scale)
  // }

  def is0: Boolean = arg.is0 || (scale == 0.0)
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.scaleconstant(
      arg.eval(runtime, params, inputs, memory), 
      scale)
  }


  def simplify: AVector = {
    val sa = arg.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorScaleConstant(sa, scale)
    }
  }

  override def toString: String = "scale(" + arg.toString + ", " + scale.toString + ")"
}

case class AVectorCat(val arg1: AVector, val arg2: AVector) extends AVector {
  val arity: Int = arg1.arity
  val input: InputDesc = arg1.input
  val size: IRPoly = arg1.size + arg2.size

  if(arg1.arity != arg2.arity) throw new IRValidationException()
  if(arg1.input != arg2.input) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorCat(arg1.arityOp(op), arg2.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorCat(arg1.inputOp(op), arg2.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.cat(arg1.translate, arg2.translate)
  // }

  def is0: Boolean = arg1.is0 && arg2.is0
  def isPure: Boolean = arg1.isPure && arg2.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.cat(
      arg1.eval(runtime, params, inputs, memory), 
      arg2.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa1 = arg1.simplify
    val sa2 = arg2.simplify
    if(sa1.is0 && sa2.is0) {
      AVectorZero(input, size)
    }
    else if(sa1.size == IRPoly.const(0, arity)) {
      sa2
    }
    else if(sa2.size == IRPoly.const(0, arity)) {
      sa1
    }
    else {
      AVectorCat(sa1, sa2)
    }
  }

  override def toString: String = "cat(" + arg1.toString + ", " + arg2.toString + ")"
}

case class AVectorCatFor(val len: IRPoly, val arg: AVector) extends AVector {
  val arity: Int = len.arity
  val input: InputDesc = arg.input.demote
  val size: IRPoly = arg.size.sum(arity).substituteAt(arity, len)

  if(len.arity + 1 != arg.arity) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = {
    if(op.xs.length != arity) throw new IRValidationException()
    AVectorCatFor(len.arityOp(op), arg.arityOp(op.leftPromote))
  }
  def inputOp(op: InputOp): AVector = AVectorCatFor(len, arg.inputOp(op.promote))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.catfor(len, arg.translate(e.promote))
  // }

  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure  

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.catfor(
      len.eval(params)(runtime.intlikei),
      size.eval(params)(runtime.intlikei),
      (i => arg.eval(runtime, params :+ i, inputs, memory)))
  }


  def simplify: AVector = {
    val sb = arg.simplify
    if(sb.is0) {
      AVectorZero(input, size)
    }
    else if(sb.size == IRPoly.const(0, arity)) {
      AVectorZero(input, size)
    }
    else {
      AVectorCatFor(len, sb)
    }
  }

  override def toString: String = "catfor(" + len.toString + ": " + arg.toString + ")"
}

case class AVectorSlice(val arg: AVector, val at: IRPoly, val size: IRPoly) extends AVector {
  val arity: Int = arg.arity
  val input: InputDesc = arg.input

  if(size.arity != arity) throw new IRValidationException()
  if(at.arity != arity) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = {
    if(op.xs.length != arity) throw new IRValidationException()
    AVectorSlice(arg.arityOp(op), at.arityOp(op), size.arityOp(op))
  }
  def inputOp(op: InputOp): AVector = AVectorSlice(arg.inputOp(op), at, size)

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.slice(arg.translate, at, size)
  // }

  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.slice(
      arg.eval(runtime, params, inputs, memory),
      at.eval(params)(runtime.intlikei),
      size.eval(params)(runtime.intlikei))
  }

  def simplify: AVector = {
    val sb = arg.simplify
    if(sb.is0) {
      AVectorZero(input, size)
    }
    else if(size == IRPoly.const(0, arity)) {
      AVectorZero(input, size)
    }
    else {
      AVectorSlice(sb, at, size)
    }
  }

  override def toString: String = "slice(" + arg.toString + ", " + at.toString + ", " + size.toString + ")"
}

case class AVectorSumFor(val len: IRPoly, val arg: AVector) extends AVector {
  val arity: Int = len.arity
  val input: InputDesc = arg.input.demote
  val size: IRPoly = arg.size.demote

  if(arg.arity != len.arity + 1) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorSumFor(len.arityOp(op), arg.arityOp(op.leftPromote))
  def inputOp(op: InputOp): AVector = AVectorSumFor(len, arg.inputOp(op.promote))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.sumfor(len, arg.translate(e.promote))
  // }
  
  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.sumfor(
      len.eval(params)(runtime.intlikei),
      size.eval(params)(runtime.intlikei),
      (i => arg.eval(runtime, params :+ i, inputs, memory)))
  }

  def simplify: AVector = {
    val sb = arg.simplify
    if(sb.is0) {
      AVectorZero(input, size)
    }
    else if(sb.size == IRPoly.const(0, arity)) {
      AVectorZero(input, size)
    }
    else {
      AVectorSumFor(len, sb)
    }
  }

  override def toString: String = "sumfor(" + len.toString + ": " + arg.toString + ")"
}

case class AVectorMpyInput(val arg: AVector, val iidx: Int, val sidx: Seq[IRPoly]) extends AVector {
  val arity: Int = arg.arity
  val input: InputDesc = arg.input
  val size: IRPoly = input.args(iidx).body.codomain.substituteSeq(sidx)

  if(arg.size != input.args(iidx).body.domain.substituteSeq(sidx)) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorMpyInput(arg.arityOp(op), iidx, sidx map (s => s.arityOp(op)))
  def inputOp(op: InputOp): AVector = {
    if(op.xs.length != input.args.length) throw new IRValidationException()
    for(i <- 0 until input.args.length) {
      if(op.xs(i).body.arity != input.args(i).body.arity) throw new IRValidationException()
    }
    op.xs(iidx).body.substituteSeq(sidx).mmpy(arg.inputOp(op))
  }

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.mmpyinput(arg.translate(e), iidx, sidx)
  // }

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.matrixmpy(
      runtime.matrixget(inputs(iidx), sidx map (s => s.eval(params)(runtime.intlikei))),
      size.eval(params)(runtime.intlikei),
      arg.eval(runtime, params, inputs, memory))
  }

  def is0: Boolean = false
  def isPure: Boolean = false

  def simplify: AVector = {
    val sa = arg.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorMpyInput(sa, iidx, sidx)
    }
  }

  override def toString: String = "mpyinput(" + arg.toString + ", " + iidx.toString + ", " + sidx.toString + ")"
}

case class AVectorMpyInputT(val arg: AVector, val iidx: Int, val sidx: Seq[IRPoly]) extends AVector {
  val arity: Int = arg.arity
  val input: InputDesc = arg.input
  val size: IRPoly = input.args(iidx).body.domain.substituteSeq(sidx)

  if(arg.size != input.args(iidx).body.codomain.substituteSeq(sidx)) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorMpyInput(arg.arityOp(op), iidx, sidx map (s => s.arityOp(op)))
  def inputOp(op: InputOp): AVector = {
    if(op.xs.length != input.args.length) throw new IRValidationException()
    for(i <- 0 until input.args.length) {
      if(op.xs(i).body.arity != input.args(i).arity) throw new IRValidationException()
    }
    op.xs(iidx).body.substituteSeq(sidx).T.mmpy(arg.inputOp(op))
  }

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = translateCheck {
  //   e.mmpyinputtranspose(arg.translate(e), iidx, sidx)
  // }

  def is0: Boolean = false
  def isPure: Boolean = false

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.matrixmpytranspose(
      runtime.matrixget(inputs(iidx), sidx map (s => s.eval(params)(runtime.intlikei))),
      size.eval(params)(runtime.intlikei),
      arg.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorMpyInputT(sa, iidx, sidx)
    }
  }
  override def toString: String = "mpyinputT(" + arg.toString + ", " + iidx.toString + ", " + sidx.toString + ")"
}

case class AVectorRead(val input: InputDesc, val iidx: Int, val sidx: Seq[IRPoly]) extends AVector {
  val arity: Int = input.arity
  val size: IRPoly = input.memory(iidx).body.substituteSeq(sidx)
  
  if(sidx.length != input.memory(iidx).dims.length) throw new IRValidationException()
  for(s <- sidx) {
    if(s.arity != arity) throw new IRValidationException()
  }

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorRead(input.arityOp(op), iidx, sidx map (s => s.arityOp(op)))
  def inputOp(op: InputOp): AVector = {
    if(op.ms.length != input.memory.length) throw new IRValidationException()
    for(i <- 0 until input.memory.length) {
      if(op.ms(i).arity != input.memory(i).arity) throw new IRValidationException()
    }
    op.ms(iidx).body.substituteSeq(sidx)
  }  

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.vectorget(memory(iidx), input.memory(iidx).body.substituteSeq(sidx).eval(params)(runtime.intlikei), sidx map (s => s.eval(params)(runtime.intlikei)))
  }

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   if(e.input != input) throw new IRValidationException()
  //   e.read(iidx, sidx)
  // }

  def is0: Boolean = false
  def isPure: Boolean = false

  def simplify: AVector = this

  override def toString: String = "read(@" + iidx.toString + "[" + sidx.toString + "])"
}

case class AVectorDot(val arg1: AVector, val arg2: AVector) extends AVector {
  val input: InputDesc = arg1.input
  val arity: Int = input.arity
  val size: IRPoly = IRPoly.const(1, arity)

  if(arg1.size != arg2.size) throw new IRValidationException()
  if(arg1.input != arg2.input) throw new IRValidationException()

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorDot(arg1.arityOp(op), arg2.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorDot(arg1.inputOp(op), arg2.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.dot(arg1.translate, arg2.translate)
  // }

  def is0: Boolean = arg1.is0 || arg2.is0
  def isPure: Boolean = arg1.isPure && arg2.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.dot(
      arg1.eval(runtime, params, inputs, memory), 
      arg2.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa1 = arg1.simplify
    val sa2 = arg2.simplify
    if(sa1.is0 || sa2.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorDot(sa1, sa2)
    }
  }

  override def toString: String = "dot(" + arg1.toString + ", " + arg2.toString + ")"
}

case class AVectorMpy(val arg: AVector, val scale: AVector) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = arg.size
  val input: InputDesc = arg.input

  arityVerify()

  if(scale.size != IRPoly.const(1, arity)) throw new IRValidationException()
  if(arg.input != scale.input) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorMpy(arg.arityOp(op), scale.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorMpy(arg.inputOp(op), scale.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.mpy(arg.translate, scale.translate)
  // }

  def is0: Boolean = arg.is0 || scale.is0
  def isPure: Boolean = arg.isPure && scale.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.mpy(
      arg.eval(runtime, params, inputs, memory), 
      scale.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    val ss = scale.simplify
    if(sa.is0 || ss.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorMpy(sa, ss)
    }
  }
}

case class AVectorDiv(val arg: AVector, val scale: AVector) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = arg.size
  val input: InputDesc = arg.input

  arityVerify()

  if(scale.size != IRPoly.const(1, arity)) throw new IRValidationException()
  if(arg.input != scale.input) throw new IRValidationException()
  if(scale.is0) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorDiv(arg.arityOp(op), scale.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorDiv(arg.inputOp(op), scale.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.mpy(arg.translate, scale.translate)
  // }

  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure && scale.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.div(
      arg.eval(runtime, params, inputs, memory), 
      scale.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    val ss = scale.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorDiv(sa, ss)
    }
  }
}


case class AVectorSqrt(val arg: AVector) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = IRPoly.const(1, arity)
  val input: InputDesc = arg.input

  arityVerify()

  if(arg.size != IRPoly.const(1, arity)) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorSqrt(arg.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorSqrt(arg.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.sqrt(arg.translate)
  // }

  def is0: Boolean = arg.is0
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.sqrt(arg.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    if(sa.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorSqrt(sa)
    }
  }
}

case class AVectorNorm2(val arg: AVector) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = IRPoly.const(1, arity)
  val input: InputDesc = arg.input

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorNorm2(arg.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorNorm2(arg.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.norm2(arg.translate)
  // }

  def is0: Boolean = false //arg.is0
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.norm2(arg.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    AVectorNorm2(sa)
  }
}

//infinity norm
case class AVectorNormInf(val arg: AVector) extends AVector {
  val arity: Int = arg.arity
  val size: IRPoly = IRPoly.const(1, arity)
  val input: InputDesc = arg.input

  arityVerify()

  def arityOp(op: ArityOp): AVector = AVectorNormInf(arg.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorNormInf(arg.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.norm2(arg.translate)
  // }

  def is0: Boolean = false //arg.is0
  def isPure: Boolean = arg.isPure

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.norm_inf(arg.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa = arg.simplify
    AVectorNormInf(sa)
  }
}

case class AVectorMax(val arg1: AVector, val arg2: AVector) extends AVector {
  val arity: Int = arg1.arity
  val size: IRPoly = arg1.size
  val input: InputDesc = arg1.input

  arityVerify()

  if(arg1.input != arg2.input) throw new IRValidationException()
  if(arg1.size != arg2.size) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorMax(arg1.arityOp(op), arg2.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorMax(arg1.inputOp(op), arg2.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.max(arg1.translate, arg2.translate)
  // }

  def is0: Boolean = arg1.is0 && arg2.is0
  def isPure: Boolean = arg1.isPure && arg2.is0

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.max(
      arg1.eval(runtime, params, inputs, memory), 
      arg2.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa1 = arg1.simplify
    val sa2 = arg2.simplify
    if(sa1.is0 && sa2.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorMax(sa1, sa2)
    }
  }
}

case class AVectorMin(val arg1: AVector, val arg2: AVector) extends AVector {
  val arity: Int = arg1.arity
  val size: IRPoly = arg1.size
  val input: InputDesc = arg1.input

  arityVerify()

  if(arg1.input != arg2.input) throw new IRValidationException()
  if(arg1.size != arg2.size) throw new IRValidationException()

  def arityOp(op: ArityOp): AVector = AVectorMin(arg1.arityOp(op), arg2.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorMin(arg1.inputOp(op), arg2.inputOp(op))

  // def translate[V <: HasInput[V]](implicit e: AVectorLike[V]): V = {
  //   e.max(arg1.translate, arg2.translate)
  // }

  def is0: Boolean = arg1.is0 && arg2.is0
  def isPure: Boolean = arg1.isPure && arg2.is0

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.min(
      arg1.eval(runtime, params, inputs, memory), 
      arg2.eval(runtime, params, inputs, memory))
  }

  def simplify: AVector = {
    val sa1 = arg1.simplify
    val sa2 = arg2.simplify
    if(sa1.is0 && sa2.is0) {
      AVectorZero(input, size)
    }
    else {
      AVectorMin(sa1, sa2)
    }
  }
}

case class AVectorTolerance(val input: InputDesc) extends AVector {
  val arity: Int = input.arity
  val size: IRPoly = IRPoly.const(1, arity)

  def arityOp(op: ArityOp): AVector = AVectorTolerance(input.arityOp(op))
  def inputOp(op: InputOp): AVector = AVectorTolerance(op.input)

  def is0: Boolean = false
  def isPure: Boolean = false

  def eval[I, M, N, V, W](
    runtime: SolverRuntime[I, M, N, V, W], 
    params: Seq[I], 
    inputs: Seq[N],
    memory: Seq[W]): V = evalcheck(runtime, params, inputs, memory)
  {
    runtime.tolerance()
  }

  def simplify: AVector = this
}
