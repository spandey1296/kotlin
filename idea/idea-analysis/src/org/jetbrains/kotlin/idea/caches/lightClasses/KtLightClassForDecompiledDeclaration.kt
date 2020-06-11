/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.lightClasses

import com.intellij.openapi.util.Pair
import com.intellij.psi.*
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassBase
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.asJava.propertyNameByAccessor
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.idea.decompiler.classFile.KtClsFile
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration

class KtLightClassForDecompiledDeclaration(
    override val clsDelegate: PsiClass,
    private val clsParent: PsiElement,
    private val file: KtClsFile,
    override val kotlinOrigin: KtClassOrObject?
) : KtLightElementBase(clsParent), PsiClass, KtLightClass {

    constructor(
        clsDelegate: PsiClass,
        kotlinOrigin: KtClassOrObject?,
        file: KtClsFile,
    ) : this(
        clsDelegate = clsDelegate,
        clsParent = file,
        file = file,
        kotlinOrigin = kotlinOrigin,
    )

    override fun findFieldByName(@NonNls name: String?, checkBases: Boolean): PsiField? =
        PsiClassImplUtil.findFieldByName(this, name, checkBases)

    override fun hasModifierProperty(p0: String): Boolean =
        clsDelegate.hasModifierProperty(p0)

    override fun findMethodBySignature(patternMethod: PsiMethod?, checkBases: Boolean): PsiMethod? =
        patternMethod?.let { PsiClassImplUtil.findMethodBySignature(this, it, checkBases) }

    override fun findInnerClassByName(myClass: String?, checkBases: Boolean): PsiClass? =
        myClass?.let { PsiClassImplUtil.findInnerByName(this, it, checkBases) }

    override fun findMethodsBySignature(patternMethod: PsiMethod?, checkBases: Boolean): Array<PsiMethod?> =
        patternMethod?.let { PsiClassImplUtil.findMethodsBySignature(this, it, checkBases) } ?: emptyArray()

    override fun findMethodsByName(@NonNls name: String?, checkBases: Boolean): Array<PsiMethod?> =
        PsiClassImplUtil.findMethodsByName(this, name, checkBases)

    override fun findMethodsAndTheirSubstitutorsByName(@NonNls name: String?, checkBases: Boolean): List<Pair<PsiMethod, PsiSubstitutor>> =
        PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases)

    override fun getImplementsList(): PsiReferenceList? = clsDelegate.implementsList

    override fun getRBrace(): PsiElement? = null

    override fun getLBrace(): PsiElement? = null

    override fun getInitializers(): Array<PsiClassInitializer> = clsDelegate.initializers

    override fun getContainingClass(): PsiClass? = parent as? PsiClass

    override fun isInheritorDeep(p0: PsiClass?, p1: PsiClass?): Boolean = clsDelegate.isInheritorDeep(p0, p1)

    override fun getAllMethodsAndTheirSubstitutors(): List<Pair<PsiMethod?, PsiSubstitutor?>?> =
        PsiClassImplUtil.getAllWithSubstitutorsByMap<PsiMethod>(this, PsiClassImplUtil.MemberType.METHOD)

    override fun isInterface(): Boolean = clsDelegate.isInterface

    override fun getTypeParameters(): Array<PsiTypeParameter> =
        clsDelegate.typeParameters

    override fun isInheritor(p0: PsiClass, p1: Boolean): Boolean =
        clsDelegate.isInheritor(p0, p1)

//    override fun processDeclarations(
//        processor: PsiScopeProcessor,
//        state: ResolveState,
//        lastParent: PsiElement?,
//        place: PsiElement
//    ): Boolean = PsiClassImplUtil.processDeclarationsInClass(
//        this,
//        processor,
//        state,
//        null,
//        lastParent,
//        place,
//        PsiUtil.getLanguageLevel(place),
//        false
//    )

    override fun processDeclarations(
        processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement
    ): Boolean {
        return clsDelegate.processDeclarations(processor, state, lastParent, place)
//        if (isEnum) {
//            if (!processDeclarationsInEnum(processor, state)) return false
//        }
//        return super.processDeclarations(processor, state, lastParent, place)
    }

    private val VALUES_METHOD = "values"
    private val VALUE_OF_METHOD = "valueOf"

    // Copy of PsiClassImplUtil.processDeclarationsInEnum for own cache class
    fun processDeclarationsInEnum(
        processor: PsiScopeProcessor,
        state: ResolveState
    ): Boolean {
        val classHint = processor.getHint(ElementClassHint.KEY)
        if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
            val nameHint = processor.getHint(NameHint.KEY)
            if (nameHint == null || VALUES_METHOD == nameHint.getName(state)) {
                val method = getValuesMethod()
                if (method != null && !processor.execute(method, ResolveState.initial())) return false
            }
            if (nameHint == null || VALUE_OF_METHOD == nameHint.getName(state)) {
                val method = getValueOfMethod()
                if (method != null && !processor.execute(method, ResolveState.initial())) return false
            }
        }

        return true
    }


    private val _makeValuesMethod: PsiMethod by lazyPub { this.makeValuesMethod() }

    fun getValuesMethod(): PsiMethod? = if (isEnum && name != null) _makeValuesMethod else null

    private val _makeValueOfMethod: PsiMethod by lazyPub { this.makeValueOfMethod() }

    fun getValueOfMethod(): PsiMethod? = if (isEnum && name != null) _makeValueOfMethod else null

    private fun makeValuesMethod(): PsiMethod {
        return getSyntheticMethod("public static " + name + "[] values() { }")
    }

    private fun makeValueOfMethod(): PsiMethod {
        return getSyntheticMethod("public static " + name + " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException { }")
    }

    private fun getSyntheticMethod(text: String): PsiMethod {
        val factory = JavaPsiFacade.getElementFactory(project)
        val method = factory.createMethodFromText(text, this)
        return object : LightMethod(this.manager, method, this) {
            override fun getTextOffset(): Int {
                return this@KtLightClassForDecompiledDeclaration.textOffset
            }
        }
    }






    override fun isEnum(): Boolean = clsDelegate.isEnum

    override fun getExtendsListTypes(): Array<PsiClassType?> =
        PsiClassImplUtil.getExtendsListTypes(this)

    override fun getTypeParameterList(): PsiTypeParameterList? = clsDelegate.typeParameterList

    override fun isAnnotationType(): Boolean = clsDelegate.isAnnotationType

    override fun getNameIdentifier(): PsiIdentifier? = clsDelegate.nameIdentifier

    override fun getInterfaces(): Array<PsiClass> =
        PsiClassImplUtil.getInterfaces(this)

    override fun getSuperClass(): PsiClass? =
        PsiClassImplUtil.getSuperClass(this)

    override fun getSupers(): Array<PsiClass> =
        PsiClassImplUtil.getSupers(this)

    override fun getSuperTypes(): Array<PsiClassType> =
        PsiClassImplUtil.getSuperTypes(this)

    override fun getVisibleSignatures(): MutableCollection<HierarchicalMethodSignature> =
        PsiSuperMethodImplUtil.getVisibleSignatures(this)

    override fun getQualifiedName(): String? = clsDelegate.qualifiedName

    override fun getImplementsListTypes(): Array<PsiClassType?> =
        PsiClassImplUtil.getImplementsListTypes(this)

    override fun getConstructors(): Array<PsiMethod> = PsiImplUtil.getConstructors(this)

    override fun isDeprecated(): Boolean = clsDelegate.isDeprecated

    override fun setName(p0: String): PsiElement = clsDelegate.setName(p0)

    override fun hasTypeParameters(): Boolean =
        PsiImplUtil.hasTypeParameters(this)

    override fun getExtendsList(): PsiReferenceList? = clsDelegate.extendsList

    override fun getDocComment(): PsiDocComment? = clsDelegate.docComment

    override fun getModifierList(): PsiModifierList? = clsDelegate.modifierList

    override fun getScope(): PsiElement = clsDelegate.scope

    override fun getAllInnerClasses(): Array<PsiClass> = PsiClassImplUtil.getAllInnerClasses(this)

    override fun getAllMethods(): Array<PsiMethod> = PsiClassImplUtil.getAllMethods(this)

    override fun getAllFields(): Array<PsiField> = PsiClassImplUtil.getAllFields(this)

    private val _methods: Array<PsiMethod> by lazyPub {
        clsDelegate.methods.map { psiMethod ->
            FUN2(
                funDelegate = psiMethod,
                funParent = this,
                lightMemberOrigin = LightMemberOriginForCompiledMethod(psiMethod, file)
            )
        }.toTypedArray()
    }

    private val _fields: Array<PsiField> by lazyPub {
        clsDelegate.fields.map { psiField ->
            FLD2(
                fldDelegate = psiField,
                fldParent = this,
                lightMemberOrigin = LightMemberOriginForCompiledField(psiField, file)
            )
        }.toTypedArray()
    }

    private val _innerClasses: Array<PsiClass> by lazyPub {
        clsDelegate.innerClasses.map { psiClass ->
            val innerDeclaration = kotlinOrigin
                ?.declarations
                ?.filterIsInstance<KtClassOrObject>()
                ?.firstOrNull { it.name == clsDelegate.name }

            KtLightClassForDecompiledDeclaration(
                clsDelegate = psiClass,
                clsParent = this,
                file = file,
                kotlinOrigin = innerDeclaration,
            )
        }.toTypedArray()
    }

    override fun getInnerClasses(): Array<PsiClass> = _innerClasses

    override fun getMethods(): Array<PsiMethod> = _methods

    override fun getFields(): Array<PsiField> = _fields

    override val originKind: LightClassOriginKind = LightClassOriginKind.BINARY

    override fun getNavigationElement() = kotlinOrigin?.navigationElement ?: file

    val fqName = kotlinOrigin?.fqName ?: FqName(qualifiedName.orEmpty())

    override fun equals(other: Any?): Boolean =
        other is KtLightClassForDecompiledDeclaration && fqName == other.fqName && kotlinOrigin == other.kotlinOrigin

    override fun hashCode(): Int = clsDelegate.hashCode()

    override fun copy(): PsiElement = this

    override fun clone(): Any = this

    override fun toString(): String = "${this.javaClass.simpleName} of $parent"

    override fun getName(): String? = clsDelegate.name

    override fun isValid(): Boolean = file.isValid && clsDelegate.isValid && (kotlinOrigin?.isValid != false)
}

class FUN2(
    private val funDelegate: PsiMethod,
    private val funParent: KtLightClass,
    override val lightMemberOrigin: LightMemberOriginForCompiledMethod,
) : KtLightElementBase(funParent), PsiMethod, KtLightMethod, KtLightMember<PsiMethod> {

    override val kotlinOrigin: KtDeclaration? get() = lightMemberOrigin.originalElement

    //CP
    override val isMangled: Boolean
        get() {
            val demangledName = KotlinTypeMapper.InternalNameMapper.demangleInternalName(name) ?: return false
            val originalName = propertyNameByAccessor(demangledName, this) ?: demangledName
            return originalName == kotlinOrigin?.name
        }

    override fun hasModifierProperty(p0: String): Boolean = funDelegate.hasModifierProperty(p0)

    override fun getReturnTypeElement(): PsiTypeElement? = funDelegate.returnTypeElement

    override fun getContainingClass(): KtLightClass = funParent

    override fun getTypeParameters(): Array<PsiTypeParameter> = funDelegate.typeParameters

    override fun getThrowsList(): PsiReferenceList = funDelegate.throwsList

    override fun getReturnType(): PsiType? = funDelegate.returnType

    override fun hasTypeParameters(): Boolean = funDelegate.hasTypeParameters()

    override fun getTypeParameterList(): PsiTypeParameterList? = funDelegate.typeParameterList

    override fun isVarArgs(): Boolean = funDelegate.isVarArgs

    override fun isConstructor(): Boolean = funDelegate.isConstructor

    override fun getNameIdentifier(): PsiIdentifier? = funDelegate.nameIdentifier

    override fun getName(): String = funDelegate.name

    override fun getDocComment(): PsiDocComment? = funDelegate.docComment

    override fun getModifierList(): PsiModifierList = funDelegate.modifierList

    override fun getBody(): PsiCodeBlock? = null

    override fun getDefaultValue(): PsiAnnotationMemberValue? = (funDelegate as? PsiAnnotationMethod)?.defaultValue

    override fun isDeprecated(): Boolean = funDelegate.isDeprecated

    override fun setName(p0: String): PsiElement = funDelegate.setName(p0)

    override fun getParameterList(): PsiParameterList = funDelegate.parameterList

    override fun getHierarchicalMethodSignature() = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)

    override fun findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List<MethodSignatureBackedByPsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

    override fun findDeepestSuperMethod() = PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

    override fun findDeepestSuperMethods(): Array<out PsiMethod> = PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

    override fun findSuperMethods(): Array<out PsiMethod> = PsiSuperMethodImplUtil.findSuperMethods(this)

    override fun findSuperMethods(checkAccess: Boolean): Array<out PsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess)

    override fun findSuperMethods(parentClass: PsiClass?): Array<out PsiMethod> =
        PsiSuperMethodImplUtil.findSuperMethods(this, parentClass)

    override fun getSignature(substitutor: PsiSubstitutor): MethodSignature =
        MethodSignatureBackedByPsiMethod.create(this, substitutor)

    override fun equals(other: Any?): Boolean = other is FUN2 && funParent == other.funParent && funDelegate == other.funDelegate

    override fun hashCode(): Int = funDelegate.hashCode()

    override fun copy(): PsiElement = this

    override fun clone(): Any = this

    override fun toString(): String = "${this.javaClass.simpleName} of $funParent"

    override val clsDelegate: PsiMethod = funDelegate

    override fun isValid(): Boolean = parent.isValid
}

class FLD2(
    private val fldDelegate: PsiField,
    private val fldParent: KtLightClass,
    override val lightMemberOrigin: LightMemberOriginForCompiledField
) : KtLightElementBase(fldParent), PsiField, KtLightField, KtLightMember<PsiField> {

    override val kotlinOrigin: KtDeclaration? get() = lightMemberOrigin.originalElement

    override fun hasModifierProperty(p0: String): Boolean = fldDelegate.hasModifierProperty(p0)

    override fun setInitializer(p0: PsiExpression?) {
        fldDelegate.initializer = p0
    }

    override fun getContainingClass(): KtLightClass = fldParent

    override fun normalizeDeclaration() = fldDelegate.normalizeDeclaration()

    override fun getNameIdentifier(): PsiIdentifier = fldDelegate.nameIdentifier

    override fun getName(): String = fldDelegate.name

    override fun getInitializer(): PsiExpression? = fldDelegate.initializer

    override fun getDocComment(): PsiDocComment? = fldDelegate.docComment

    override fun getTypeElement(): PsiTypeElement? = fldDelegate.typeElement

    override fun getModifierList(): PsiModifierList? = fldDelegate.modifierList

    override fun hasInitializer(): Boolean = fldDelegate.hasInitializer()

    override fun getType(): PsiType = fldDelegate.type

    override fun isDeprecated(): Boolean = fldDelegate.isDeprecated

    override fun setName(p0: String): PsiElement = fldDelegate.setName(p0)

    override fun computeConstantValue(): Any? = fldDelegate.computeConstantValue()

    override fun computeConstantValue(p0: MutableSet<PsiVariable>?): Any? = fldDelegate.computeConstantValue()

    override fun equals(other: Any?): Boolean = other is FLD2 && fldParent == other.fldParent && fldDelegate == other.fldDelegate

    override fun hashCode(): Int = fldDelegate.hashCode()

    override fun copy(): PsiElement = this

    override fun clone(): Any = this

    override fun toString(): String = "${this.javaClass.simpleName} of $fldParent"

    override val clsDelegate: PsiField = fldDelegate

    override fun isValid(): Boolean = parent.isValid
}


class KtLightClassForDecompiledDeclaration1(
    override val clsDelegate: ClsClassImpl,
    override val kotlinOrigin: KtClassOrObject?,
    private val file: KtClsFile
) : KtLightClassBase(clsDelegate.manager) {
    val fqName = kotlinOrigin?.fqName ?: FqName(clsDelegate.qualifiedName.orEmpty())

    override fun copy() = this

    override fun getOwnInnerClasses(): List<PsiClass> {
        val nestedClasses = kotlinOrigin?.declarations?.filterIsInstance<KtClassOrObject>() ?: emptyList()
        return clsDelegate.ownInnerClasses.map { innerClsClass ->
            KtLightClassForDecompiledDeclaration(
                innerClsClass as ClsClassImpl,
                nestedClasses.firstOrNull { innerClsClass.name == it.name }, file
            )
        }
    }

    override fun getOwnFields(): List<PsiField> {
        return clsDelegate.ownFields.map { KtLightFieldImpl.create(LightMemberOriginForCompiledField(it, file), it, this) }
    }

    override fun getOwnMethods(): List<PsiMethod> {
        return clsDelegate.ownMethods.map { KtLightMethodImpl.create(it, LightMemberOriginForCompiledMethod(it, file), this) }
    }

    override fun getNavigationElement() = kotlinOrigin?.navigationElement ?: file

    override fun getParent() = clsDelegate.parent

    override fun equals(other: Any?): Boolean =
        other is KtLightClassForDecompiledDeclaration &&
                fqName == other.fqName

    override fun hashCode(): Int =
        fqName.hashCode()

    override val originKind: LightClassOriginKind
        get() = LightClassOriginKind.BINARY
}

