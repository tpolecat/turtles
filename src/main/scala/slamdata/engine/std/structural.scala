package slamdata.engine.std

import scalaz._
import Validation.{success, failure}
import NonEmptyList.nel

import slamdata.engine.{Mapping, Data, SemanticError, Type}

import Type._
import SemanticError._

trait StructuralLib extends Library {
  import Validation.{success, failure}

  val MakeObject = Mapping("MAKE_OBJECT", "Makes a singleton object containing a single field", Str :: Top :: Nil, partialTyper {
    case Const(Data.Str(name)) :: Const(data) :: Nil => Const(Data.Obj(Map(name -> data)))
    case Const(Data.Str(name)) :: (valueType) :: Nil => (NamedField(name, valueType))
    case _ :: Const(data) :: Nil => (AnonField(data.dataType))
    case _ :: valueType :: Nil => (AnonField(valueType))
  }, {
    case Const(Data.Obj(map)) => map.head match { case (key, value) => success(Const(Data.Str(key)) :: Const(value) :: Nil) }
    case NamedField(name, valueType) => success(Const(Data.Str(name)) :: valueType :: Nil)
    case AnonField(tpe) => success(Str :: tpe :: Nil)
    case t => failure(nel(TypeError(AnyObject, t), Nil))
  })

  val MakeArray = Mapping("MAKE_ARRAY", "Makes a singleton array containing a single element", Top :: Nil, partialTyper {
    case Const(data) :: Nil => Const(Data.Arr(data :: Nil))
    case (valueType) :: Nil => (AnonElem(valueType))
    case _ => AnyArray
  }, {
    case Const(Data.Arr(arr)) => success(Const(arr.head) :: Nil)    
    case AnonElem(elemType) => success(elemType :: Nil)
    case t => failure(nel(TypeError(AnyArray, t), Nil))
  })

  val ObjectConcat = Mapping("OBJECT_CONCAT", "A right-biased merge of two objects into one object", AnyObject :: AnyObject :: Nil, partialTyper {
    case Const(Data.Obj(map1)) :: Const(Data.Obj(map2)) :: Nil => Const(Data.Obj(map1 ++ map2))
    case v1 :: v2 :: Nil => (v1 & v2)
  }, {
    case x if x.objectLike => success(AnyObject :: AnyObject :: Nil)
    case x => failure(nel(TypeError(AnyObject, x), Nil))
  })

  val ArrayConcat = Mapping("ARRAY_CONCAT", "A right-biased merge of two arrays into one array", AnyArray :: AnyArray :: Nil, partialTyper {
    case Const(Data.Arr(els1)) :: Const(Data.Arr(els2)) :: Nil => Const(Data.Arr(els1 ++ els2))
    case v1 :: v2 :: Nil => (v1 & v2) // TODO: Unify het array into hom array
  }, {
    case x if x.arrayLike => success(AnyArray :: AnyArray :: Nil)
    case x => failure(nel(TypeError(AnyArray, x), Nil))
  })

  val ObjectProject = Mapping("({})", "Extracts a specified field of an object", AnyObject :: Str :: Nil, partialTyperV {
    case v1 :: v2 :: Nil => v1.objectField(v2)
  }, {    
    case x => success(AnonField(x) :: Str :: Nil)
  })

  val ArrayProject = Mapping("([])", "Extracts a specified index of an array", AnyArray :: Int :: Nil, partialTyperV {
    case v1 :: v2 :: Nil => v1.arrayElem(v2)
  }, {
    case x => success(AnonElem(x) :: Int :: Nil)
  })

  val DeleteField = Mapping("DELETE_FIELD", "Deletes a field inside an object", AnyObject :: Str :: Nil, partialTyper {
    case Const(Data.Obj(map)) :: Const(Data.Str(name)) :: Nil => Const(Data.Obj(map - name))
    case v1 :: Const(Data.Str(name)) :: Nil => (Top) // TODO: See if we can infer type based on field name
    case v1 :: v2 :: Nil => (Top)
  }, {
    case x if (x.objectLike) => success(AnyObject :: Str :: Nil) // TODO: Fix
    case x => failure(nel(TypeError(AnyObject, x), Nil))
  })

  val DeleteIndex = Mapping("DELETE_INDEX", "Deletes an element inside an array", AnyArray :: Int :: Nil, partialTyper {
    case Const(Data.Arr(els)) :: Const(Data.Int(idx)) :: Nil => 
      val index = idx.toInt
      Const(Data.Arr(els.take(index) ++ els.drop(index + 1)))

    case v1 :: Const(Data.Int(idx)) :: Nil => (Top) // TODO: See if we can infer type based on idx
    case v1 :: v2 :: Nil => (Top)
  }, {
    case x if (x.arrayLike) => success(AnyArray :: Str :: Nil) // TODO: Fix
    case x => failure(nel(TypeError(AnyArray, x), Nil))
  })

  def functions = MakeObject :: MakeArray :: 
                  ObjectConcat :: ArrayConcat :: 
                  ObjectProject :: ArrayProject :: 
                  DeleteField :: DeleteIndex :: Nil
}
object StructuralLib extends StructuralLib