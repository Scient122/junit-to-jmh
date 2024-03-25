import json
import pandas
import math
import sys
import os 

def filtering(input,output):
    input_file_path = input
    output_file_path = output

    output_file_name = input_file_path[:input_file_path.find('.json')]+'Filtered.json'
    output_file_name = output_file_path + output_file_name[output_file_name.rfind("\\")+1:]

    with open(input_file_path) as f:
        data = json.load(f)

    df = pandas.read_json(input_file_path)
    lowIndex = 0
    highIndex = 200
    subsetsNumber = math.ceil((len(df)/200))
    datasubsets = []
    i = 0

    while i < subsetsNumber:
        dfslice = df.iloc[lowIndex:highIndex,:]
        quantilesDifference = dfslice['average'].quantile(0.99) - dfslice['average'].quantile(0.1)
        median = dfslice['average'].median()
        inferiorBound = median - (3 * quantilesDifference)
        superiorBound = median + (3 * quantilesDifference)
        dfslice = dfslice[dfslice['average'] >= inferiorBound]
        dfslice = dfslice[dfslice['average'] <= superiorBound]
        datasubsets.append(dfslice)
        lowIndex = highIndex
        highIndex += 200
        i+=1

    finaldataset = datasubsets[0]
    i = 1
    while i < subsetsNumber:
        finaldataset = pandas.concat([finaldataset, datasubsets[i]])
        i = i+1

    with open(output_file_name, 'w') as json_file:
        jsonObject = pandas.DataFrame.to_json(finaldataset, orient='records')
        json_file.write(jsonObject)


input_files_directory = sys.argv[1]
output_files_path = sys.argv[2]
files = os.listdir(input_files_directory)
for file in files:
    filtering(input_files_directory+"\\"+file,output_files_path)