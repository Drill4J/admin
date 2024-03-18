package com.epam.drill.admin.writer.rawdata.config

import com.epam.drill.admin.writer.rawdata.service.RawDataReader
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import com.epam.drill.admin.writer.rawdata.service.impl.RawDataServiceImpl
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val rawDataWriterDIModule = DI.Module("rawDataWriterServices") {
    bind<RawDataWriter>() with singleton { RawDataServiceImpl() }
    bind<RawDataReader>() with singleton { RawDataServiceImpl() }
}