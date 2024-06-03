package io.deepmedia.tools.knee.plugin.compiler.features

/* class KneeImport(source: IrClass) : KneeFeature<IrClass>(source, "KneeImport") {

    val interfaces: List<KneeInterface>
    val enums: List<KneeEnum>
    val classes: List<KneeClass>

    init {
        val interfaces = mutableListOf<KneeInterface>()
        val enums = mutableListOf<KneeEnum>()
        val classes = mutableListOf<KneeClass>()
        source.requireNotComplex(this, ClassKind.INTERFACE)
        source.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) = Unit

            override fun visitProperty(declaration: IrProperty) {
                if (declaration.hasAnnotation(kneeInterfaceAnnotation)) {
                    val type = (declaration.backingField?.type ?: declaration.getter?.returnType)!! as IrSimpleType
                    val info = ImportInfo(type, declaration)
                    interfaces.add(KneeInterface(
                        source = type.classOrNull!!.owner,
                        importInfo = info
                    ))
                } else if (declaration.hasAnnotation(kneeEnumAnnotation)) {
                    val type = (declaration.backingField?.type ?: declaration.getter?.returnType)!! as IrSimpleType
                    val info = ImportInfo(type, declaration)
                    enums.add(KneeEnum(
                        source = type.classOrNull!!.owner,
                        importInfo = info
                    ))
                }else if (declaration.hasAnnotation(kneeClassAnnotation)) {
                    val type = (declaration.backingField?.type ?: declaration.getter?.returnType)!! as IrSimpleType
                    val info = ImportInfo(type, declaration)
                    classes.add(KneeClass(
                        source = type.classOrNull!!.owner,
                        importInfo = info
                    ))
                }
            }
        })
        this.interfaces = interfaces
        this.enums = enums
        this.classes = classes
    }
} */
