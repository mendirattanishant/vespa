// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_binary_format.h"
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/objects/nbostream.h>


using vespalib::nbostream;

namespace vespalib {
namespace tensor {

namespace {

eval::ValueType
makeValueType(std::vector<eval::ValueType::Dimension> &&dimensions) {
    return (dimensions.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(std::move(dimensions)));
}

}

void
DenseBinaryFormat::serialize(nbostream &stream, const DenseTensor &tensor)
{
    stream.putInt1_4Bytes(tensor.type().dimensions().size());
    size_t cellsSize = 1;
    for (const auto &dimension : tensor.type().dimensions()) {
        stream.writeSmallString(dimension.name);
        stream.putInt1_4Bytes(dimension.size);
        cellsSize *= dimension.size;
    }
    const DenseTensor::Cells &cells = tensor.cells();
    assert(cells.size() == cellsSize);
    for (const auto &value : cells) {
        stream << value;
    }
}


std::unique_ptr<DenseTensor>
DenseBinaryFormat::deserialize(nbostream &stream)
{
    vespalib::string dimensionName;
    std::vector<eval::ValueType::Dimension> dimensions;
    DenseTensor::Cells cells;
    size_t dimensionsSize = stream.getInt1_4Bytes();
    size_t dimensionSize;
    size_t cellsSize = 1;
    while (dimensions.size() < dimensionsSize) {
        stream.readSmallString(dimensionName);
        dimensionSize = stream.getInt1_4Bytes();
        dimensions.emplace_back(dimensionName, dimensionSize);
        cellsSize *= dimensionSize;
    }
    cells.reserve(cellsSize);
    double cellValue = 0.0;
    for (size_t i = 0; i < cellsSize; ++i) {
        stream >> cellValue;
        cells.emplace_back(cellValue);
    }
    return std::make_unique<DenseTensor>(makeValueType(std::move(dimensions)),
                                         std::move(cells));
}


} // namespace vespalib::tensor
} // namespace vespalib